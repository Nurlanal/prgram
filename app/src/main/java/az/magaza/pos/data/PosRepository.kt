package az.magaza.pos.data

import androidx.room.withTransaction
import az.magaza.pos.data.db.*
import az.magaza.pos.util.Barcodes
import az.magaza.pos.util.Money

/** İstifadəçiyə göstəriləcək biznes xətası. */
class PosException(message: String) : Exception(message)

data class CartLine(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val unitPrice: Long,
)

/**
 * Bütün biznes əməliyyatları burada, tək tranzaksiya daxilində aparılır ki,
 * satış, anbar, kassa və borc hesabları hər zaman bir-birinə uyğun olsun.
 */
class PosRepository(
    val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    val dao: PosDao = db.dao()

    private suspend fun log(action: String, details: String) {
        dao.insertLog(ActivityLog(createdAt = clock(), action = action, details = details))
    }

    // ======================= MƏHSULLAR =======================

    suspend fun saveProduct(p: Product): Long = db.withTransaction {
        val name = p.name.trim()
        if (name.isEmpty()) throw PosException("Məhsul adı boş ola bilməz")
        if (p.stock < 0) throw PosException("Anbar sayı mənfi ola bilməz")
        if (p.minStock < 0) throw PosException("Minimum stok mənfi ola bilməz")
        if (p.purchasePrice < 0 || p.salePrice < 0) throw PosException("Qiymət mənfi ola bilməz")
        if (p.barcode.isNotBlank()) {
            dao.findDuplicateCode(p.barcode.trim(), p.id)?.let {
                throw PosException("Bu barkod artıq \"${it.name}\" məhsulunda var")
            }
        }
        val clean = p.copy(name = name, barcode = p.barcode.trim(), sku = p.sku.trim(), brand = p.brand.trim())
        if (p.id == 0L) {
            val withCost = clean.copy(createdAt = clock(), stockCost = clean.stock.toLong() * clean.purchasePrice)
            val id = dao.insertProduct(withCost)
            if (clean.stock > 0) {
                dao.insertStockMove(
                    StockMove(productId = id, productName = name, createdAt = clock(), type = StockType.INITIAL,
                        qtyChange = clean.stock, stockAfter = clean.stock, note = "İlkin qalıq")
                )
            }
            log("Məhsul əlavə edildi", "$name, qalıq: ${clean.stock}, alış: ${Money.formatAzn(clean.purchasePrice)}, " +
                "satış: ${Money.formatAzn(clean.salePrice)}, anbar dəyəri: ${Money.formatAzn(withCost.stockCost)}")
            id
        } else {
            val old = dao.getProduct(p.id) ?: throw PosException("Məhsul tapılmadı")
            // Alış qiyməti əl ilə dəyişdirilibsə — bütün qalıq yeni qiymətlə qiymətləndirilir.
            // Yalnız say dəyişibsə — dəyər mövcud orta mayaya görə (qəpiyinə qədər) düzəldilir.
            val stockCost = when {
                old.purchasePrice != clean.purchasePrice -> clean.stock.toLong() * clean.purchasePrice
                old.stock != clean.stock -> Cost.afterRecount(old, clean.stock)
                else -> old.stockCost
            }
            val saved = clean.copy(createdAt = old.createdAt, stockCost = stockCost,
                purchasePrice = if (old.purchasePrice != clean.purchasePrice) clean.purchasePrice else Cost.avg(stockCost, clean.stock, old.purchasePrice))
            dao.updateProduct(saved)
            if (old.stock != clean.stock) {
                dao.insertStockMove(
                    StockMove(productId = p.id, productName = name, createdAt = clock(), type = StockType.ADJUST,
                        qtyChange = clean.stock - old.stock, stockAfter = clean.stock, note = "Redaktə zamanı düzəliş")
                )
            }
            val changes = buildList {
                if (old.salePrice != clean.salePrice) add("satış qiyməti ${Money.formatAzn(old.salePrice)} → ${Money.formatAzn(clean.salePrice)}")
                if (old.purchasePrice != clean.purchasePrice) add("alış qiyməti ${Money.formatAzn(old.purchasePrice)} → ${Money.formatAzn(clean.purchasePrice)}")
                if (old.stock != clean.stock) add("stok ${old.stock} → ${clean.stock}")
                if (old.stockCost != stockCost) add("anbar dəyəri ${Money.formatAzn(old.stockCost)} → ${Money.formatAzn(stockCost)}")
            }
            log("Məhsul redaktə edildi", name + if (changes.isNotEmpty()) ": " + changes.joinToString(", ") else "")
            p.id
        }
    }

    suspend fun deleteProduct(id: Long) = db.withTransaction {
        val p = dao.getProduct(id) ?: return@withTransaction
        dao.deleteProduct(id)
        log("Məhsul silindi", "${p.name} (qalıq: ${p.stock}, anbar dəyəri: ${Money.formatAzn(p.stockCost)})")
    }

    /** Bir məhsulun anbar qalığını düzəldir (sayım, zədələnmə, itki). Dəyər qəpiyinə qədər düzəldilir. */
    suspend fun adjustStock(productId: Long, newStock: Int, note: String) = db.withTransaction {
        if (newStock < 0) throw PosException("Stok mənfi ola bilməz")
        val p = dao.getProduct(productId) ?: throw PosException("Məhsul tapılmadı")
        if (p.stock == newStock) return@withTransaction
        val cost = Cost.afterRecount(p, newStock)
        dao.updateProduct(p.copy(stock = newStock, stockCost = cost, purchasePrice = Cost.avg(cost, newStock, p.purchasePrice)))
        dao.insertStockMove(
            StockMove(productId = p.id, productName = p.name, createdAt = clock(), type = StockType.ADJUST,
                qtyChange = newStock - p.stock, stockAfter = newStock, note = note.ifBlank { "Stok düzəlişi" })
        )
        log("Stok düzəldildi", "${p.name}: ${p.stock} → $newStock, dəyər ${Money.formatAzn(p.stockCost)} → ${Money.formatAzn(cost)}. $note")
    }

    data class CountResult(val changed: Int, val qtyDiff: Int, val valueDiff: Long)

    /** Anbar sayımı: faktiki sayılan qalıqları bir əməliyyatla tətbiq edir. */
    suspend fun applyStockCount(counts: Map<Long, Int>, note: String): CountResult = db.withTransaction {
        var changed = 0; var qty = 0; var value = 0L
        counts.forEach { (id, n) ->
            if (n < 0) throw PosException("Say mənfi ola bilməz")
            val p = dao.getProduct(id) ?: return@forEach
            if (p.stock == n) return@forEach
            val cost = Cost.afterRecount(p, n)
            dao.updateProduct(p.copy(stock = n, stockCost = cost, purchasePrice = Cost.avg(cost, n, p.purchasePrice)))
            dao.insertStockMove(StockMove(productId = p.id, productName = p.name, createdAt = clock(), type = StockType.ADJUST,
                qtyChange = n - p.stock, stockAfter = n, note = note.ifBlank { "Anbar sayımı" }))
            changed++; qty += n - p.stock; value += cost - p.stockCost
        }
        if (changed > 0) log("Anbar sayımı", "$changed məhsul düzəldildi, say fərqi: ${if (qty > 0) "+" else ""}$qty, " +
            "dəyər fərqi: ${Money.formatAzn(value)}. $note")
        CountResult(changed, qty, value)
    }

    /** Mövcud məhsula barkod təyin edir (məs. satış zamanı tanınmayan barkod skan edildikdə). */
    suspend fun assignBarcode(productId: Long, code: String): Product = db.withTransaction {
        val c = code.trim()
        if (c.isEmpty()) throw PosException("Barkod boş ola bilməz")
        val p = dao.getProduct(productId) ?: throw PosException("Məhsul tapılmadı")
        dao.findDuplicateCode(c, productId)?.let { throw PosException("Bu barkod artıq \"${it.name}\" məhsulundadır") }
        val updated = p.copy(barcode = c)
        dao.updateProduct(updated)
        log("Barkod təyin edildi", "${p.name}: ${p.barcode.ifBlank { "—" }} → $c")
        updated
    }

    /** Barkodu olmayan məhsul üçün unikal daxili EAN-13 barkod yaradır. */
    suspend fun generateBarcode(productId: Long): Product = db.withTransaction {
        val p = dao.getProduct(productId) ?: throw PosException("Məhsul tapılmadı")
        var attempt = 0
        var code = Barcodes.internalFor(p.id)
        while (dao.findDuplicateCode(code, p.id) != null) code = Barcodes.internalFor(p.id, ++attempt)
        assignBarcode(p.id, code)
    }

    /** Bütün barkodsuz məhsullara barkod yaradır. Yaradılanların sayını qaytarır. */
    suspend fun generateMissingBarcodes(): Int = db.withTransaction {
        val missing = dao.allProducts().filter { it.barcode.isBlank() }
        missing.forEach { generateBarcode(it.id) }
        missing.size
    }

    // ======================= SATIŞ =======================

    /**
     * Satışı tamamlayır.
     * @param cashPaid kassaya daxil olan nağd məbləğ (qaytarılan qalıq çıxılmış)
     * @param cardPaid kartla ödənilən məbləğ
     * Qalan hissə (cəm − nağd − kart) müştərinin borcuna yazılır.
     */
    suspend fun completeSale(
        lines: List<CartLine>,
        discount: Long,
        cashPaid: Long,
        cardPaid: Long,
        customerId: Long?,
        note: String = "",
        debtDueAt: Long? = null,
    ): Long = db.withTransaction {
        if (lines.isEmpty()) throw PosException("Səbət boşdur")
        if (lines.any { it.quantity <= 0 }) throw PosException("Say 0-dan böyük olmalıdır")
        if (discount < 0 || cashPaid < 0 || cardPaid < 0) throw PosException("Məbləğ mənfi ola bilməz")

        val now = clock()
        var subtotal = 0L
        lines.groupBy { it.productId }.forEach { (pid, ls) ->
            val p = dao.getProduct(pid) ?: throw PosException("\"${ls.first().name}\" məhsulu tapılmadı")
            if (ls.sumOf { it.quantity } > p.stock) {
                throw PosException("\"${p.name}\" üçün anbarda kifayət qədər məhsul yoxdur (qalıq: ${p.stock})")
            }
        }
        lines.forEach { subtotal += it.unitPrice * it.quantity }
        if (discount > subtotal) throw PosException("Endirim ümumi məbləğdən çox ola bilməz")
        val total = subtotal - discount
        if (cashPaid + cardPaid > total) throw PosException("Ödəniş məbləği ümumi məbləğdən çoxdur")
        val debt = total - cashPaid - cardPaid
        val customer = customerId?.let { dao.getCustomer(it) ?: throw PosException("Müştəri tapılmadı") }
        if (debt > 0 && customer == null) throw PosException("Borca satış üçün müştəri seçin")

        val saleId = dao.insertSale(
            Sale(
                createdAt = now, customerId = customer?.id, customerName = customer?.name ?: "",
                itemCount = lines.sumOf { it.quantity }, subtotal = subtotal, discount = discount, total = total,
                costTotal = 0, profit = 0, paidCash = cashPaid, paidCard = cardPaid,
                debtAmount = debt, note = note.trim(),
            )
        )
        // Hər sətir anbardan öz dəqiq maya dəyərini götürür (qəpiyinə qədər); stok ardıcıl azaldılır.
        var cost = 0L
        val items = lines.map { line ->
            val p = dao.getProduct(line.productId)!!
            if (line.quantity > p.stock) throw PosException("\"${p.name}\" üçün anbarda kifayət qədər məhsul yoxdur")
            val lineCost = Cost.take(p, line.quantity)
            val after = p.stock - line.quantity
            val remainingCost = p.stockCost - lineCost
            dao.updateProduct(p.copy(stock = after, stockCost = remainingCost, purchasePrice = Cost.avg(remainingCost, after, p.purchasePrice)))
            dao.insertStockMove(StockMove(productId = p.id, productName = p.name, createdAt = now,
                type = StockType.SALE, qtyChange = -line.quantity, stockAfter = after, refId = saleId,
                note = "Satış #$saleId"))
            cost += lineCost
            SaleItem(saleId = saleId, productId = p.id, productName = p.name, quantity = line.quantity,
                unitPrice = line.unitPrice, unitCost = Cost.avg(lineCost, line.quantity, p.purchasePrice),
                lineTotal = line.unitPrice * line.quantity, lineCost = lineCost)
        }
        dao.insertSaleItems(items)
        dao.updateSale(dao.getSale(saleId)!!.copy(costTotal = cost, profit = total - cost))
        if (cashPaid > 0) dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.SALE,
            method = PayMethod.CASH, amount = cashPaid, refId = saleId, note = "Satış #$saleId"))
        if (cardPaid > 0) dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.SALE,
            method = PayMethod.CARD, amount = cardPaid, refId = saleId, note = "Satış #$saleId"))
        if (debt > 0) dao.insertDebtTx(CustomerDebtTx(customerId = customer!!.id, createdAt = now,
            type = DebtType.SALE, amount = debt, saleId = saleId, note = "Satış #$saleId",
            dueAt = debtDueAt?.let { az.magaza.pos.util.Dates.startOfDay(it) }))

        log("Satış #$saleId", "Cəm: ${Money.formatAzn(total)}, nağd: ${Money.formatAzn(cashPaid)}, " +
            "kart: ${Money.formatAzn(cardPaid)}, borc: ${Money.formatAzn(debt)}" +
            (customer?.let { ", müştəri: ${it.name}" } ?: ""))
        saleId
    }

    /** Satışı ləğv edir: stok qaytarılır, kassa və borc əks yazılışla düzəldilir. */
    suspend fun cancelSale(saleId: Long, reason: String) = db.withTransaction {
        val sale = dao.getSale(saleId) ?: throw PosException("Satış tapılmadı")
        if (sale.status == SaleStatus.CANCELLED) throw PosException("Bu satış artıq ləğv edilib")
        if (reason.isBlank()) throw PosException("Ləğv səbəbini yazın")
        val now = clock()
        dao.getSaleItems(saleId).forEach { item ->
            val p = dao.getProduct(item.productId)
            if (p != null) {
                val after = p.stock + item.quantity
                val back = if (item.lineCost > 0 || item.unitCost == 0L) item.lineCost else item.unitCost * item.quantity
                val cost = p.stockCost + back
                dao.updateProduct(p.copy(stock = after, stockCost = cost, purchasePrice = Cost.avg(cost, after, p.purchasePrice)))
                dao.insertStockMove(StockMove(productId = p.id, productName = p.name, createdAt = now,
                    type = StockType.SALE_CANCEL, qtyChange = item.quantity, stockAfter = after, refId = saleId,
                    note = "Satış #$saleId ləğvi"))
            }
        }
        if (sale.paidCash > 0) dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.SALE_CANCEL,
            method = PayMethod.CASH, amount = -sale.paidCash, refId = saleId, note = "Satış #$saleId ləğvi"))
        if (sale.paidCard > 0) dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.SALE_CANCEL,
            method = PayMethod.CARD, amount = -sale.paidCard, refId = saleId, note = "Satış #$saleId ləğvi"))
        if (sale.debtAmount > 0 && sale.customerId != null) dao.insertDebtTx(CustomerDebtTx(
            customerId = sale.customerId, createdAt = now, type = DebtType.CANCEL, amount = -sale.debtAmount,
            saleId = saleId, note = "Satış #$saleId ləğvi"))
        dao.updateSale(sale.copy(status = SaleStatus.CANCELLED, cancelReason = reason.trim(), cancelledAt = now))
        log("Satış #$saleId ləğv edildi", "Səbəb: ${reason.trim()}. Məbləğ: ${Money.formatAzn(sale.total)}")
    }

    // ======================= MÜŞTƏRİ VƏ BORC =======================

    suspend fun saveCustomer(c: Customer): Long = db.withTransaction {
        if (c.name.isBlank()) throw PosException("Müştəri adı boş ola bilməz")
        val clean = c.copy(name = c.name.trim(), phone = c.phone.trim())
        if (c.id == 0L) {
            val id = dao.insertCustomer(clean.copy(createdAt = clock()))
            log("Müştəri əlavə edildi", clean.name)
            id
        } else {
            dao.updateCustomer(clean); log("Müştəri redaktə edildi", clean.name); c.id
        }
    }

    suspend fun deleteCustomer(id: Long) = db.withTransaction {
        val c = dao.getCustomer(id) ?: return@withTransaction
        if (dao.customerDebt(id) != 0L) throw PosException("Borcu olan müştərini silmək olmaz")
        dao.deleteCustomer(id)
        log("Müştəri silindi", c.name)
    }

    suspend fun addManualDebt(customerId: Long, amount: Long, note: String, dueAt: Long? = null) = db.withTransaction {
        if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
        val c = dao.getCustomer(customerId) ?: throw PosException("Müştəri tapılmadı")
        dao.insertDebtTx(CustomerDebtTx(customerId = c.id, createdAt = clock(), type = DebtType.MANUAL,
            amount = amount, note = note.trim(), dueAt = dueAt?.let { az.magaza.pos.util.Dates.startOfDay(it) }))
        log("Borc əlavə edildi", "${c.name}: ${Money.formatAzn(amount)}. $note")
    }

    /** Borcun ödəniş tarixini təyin edir / dəyişir (null — tarixi silir). */
    suspend fun setDebtDueDate(debtTxId: Long, dueAt: Long?) = db.withTransaction {
        val t = dao.getDebtTx(debtTxId) ?: throw PosException("Borc tapılmadı")
        if (t.amount <= 0) throw PosException("Yalnız borc yazılışına tarix təyin etmək olar")
        val day = dueAt?.let { az.magaza.pos.util.Dates.startOfDay(it) }
        dao.setDebtDue(debtTxId, day)
        val c = dao.getCustomer(t.customerId)
        log("Borcun ödəniş tarixi", "${c?.name ?: "-"}: ${t.note.ifBlank { Money.formatAzn(t.amount) }} → " +
            (day?.let { az.magaza.pos.util.Dates.date(it) } ?: "tarixsiz"))
    }

    /** Müştəri borcunu ödəyir; ödəniş kassaya daxil olur. */
    suspend fun payDebt(customerId: Long, amount: Long, method: String, note: String) = db.withTransaction {
        if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
        val c = dao.getCustomer(customerId) ?: throw PosException("Müştəri tapılmadı")
        val debt = dao.customerDebt(customerId)
        if (amount > debt) throw PosException("Ödəniş qalan borcdan (${Money.format(debt)}) çox ola bilməz")
        val now = clock()
        val txId = dao.insertDebtTx(CustomerDebtTx(customerId = c.id, createdAt = now, type = DebtType.PAYMENT,
            amount = -amount, method = method, note = note.trim()))
        dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.DEBT_PAYMENT, method = method,
            amount = amount, refId = txId, note = "Borc ödənişi: ${c.name}"))
        log("Borc ödənişi", "${c.name}: ${Money.formatAzn(amount)} (${methodName(method)}). Qalan: ${Money.formatAzn(debt - amount)}")
    }

    // ======================= TƏCHİZATÇI =======================

    suspend fun saveSupplier(s: Supplier): Long = db.withTransaction {
        if (s.name.isBlank()) throw PosException("Təchizatçı adı boş ola bilməz")
        val clean = s.copy(name = s.name.trim(), phone = s.phone.trim())
        if (s.id == 0L) {
            val id = dao.insertSupplier(clean.copy(createdAt = clock()))
            log("Təchizatçı əlavə edildi", clean.name); id
        } else {
            dao.updateSupplier(clean); log("Təchizatçı redaktə edildi", clean.name); s.id
        }
    }

    suspend fun deleteSupplier(id: Long) = db.withTransaction {
        val s = dao.getSupplier(id) ?: return@withTransaction
        if (dao.supplierDebt(id) != 0L) throw PosException("Borcu olan təchizatçını silmək olmaz")
        dao.deleteSupplier(id); log("Təchizatçı silindi", s.name)
    }

    suspend fun paySupplier(supplierId: Long, amount: Long, method: String, note: String) = db.withTransaction {
        if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
        val s = dao.getSupplier(supplierId) ?: throw PosException("Təchizatçı tapılmadı")
        val debt = dao.supplierDebt(supplierId)
        if (amount > debt) throw PosException("Ödəniş borcdan (${Money.format(debt)}) çox ola bilməz")
        val now = clock()
        val txId = dao.insertSupplierTx(SupplierTx(supplierId = s.id, createdAt = now, type = SupplierTxType.PAYMENT,
            amount = -amount, note = note.trim()))
        dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.SUPPLIER_PAYMENT, method = method,
            amount = -amount, refId = txId, note = "Təchizatçıya ödəniş: ${s.name}"))
        log("Təchizatçıya ödəniş", "${s.name}: ${Money.formatAzn(amount)}")
    }

    // ======================= MƏDAXİL (ALIŞ) =======================

    /**
     * Anbara məhsul daxil edir. Ödənilən hissə kassadan çıxır, qalan hissə təchizatçı borcuna yazılır.
     * Məhsulun alış qiyməti orta çəkili maya dəyəri ilə yenilənir.
     */
    suspend fun addPurchase(
        productId: Long,
        quantity: Int,
        unitCost: Long,
        supplierId: Long?,
        paidAmount: Long,
        method: String,
        date: Long,
        note: String,
        newSalePrice: Long? = null,
        /** Ümumi alış məbləği (məs. 3 ədəd cəmi 10.00 ₼). Verilərsə, vahid qiymət bundan hesablanır və anbar dəyəri dəqiq bu məbləğ qədər artır. */
        totalCost: Long? = null,
    ): Long = db.withTransaction {
        if (quantity <= 0) throw PosException("Say 0-dan böyük olmalıdır")
        if (unitCost < 0 || paidAmount < 0 || (totalCost ?: 0) < 0) throw PosException("Məbləğ mənfi ola bilməz")
        val p = dao.getProduct(productId) ?: throw PosException("Məhsul tapılmadı")
        val total = totalCost ?: (unitCost * quantity)
        val unit = if (totalCost != null) Cost.avg(totalCost, quantity, unitCost) else unitCost
        if (paidAmount > total) throw PosException("Ödənilən məbləğ ümumi alış məbləğindən çox ola bilməz")
        val supplier = supplierId?.let { dao.getSupplier(it) ?: throw PosException("Təchizatçı tapılmadı") }
        val debt = total - paidAmount
        if (debt > 0 && supplier == null) throw PosException("Borcla alış üçün təchizatçı seçin")
        val status = when {
            debt == 0L -> PurchaseStatus.PAID
            paidAmount == 0L -> PurchaseStatus.DEBT
            else -> PurchaseStatus.PARTIAL
        }
        val newStock = p.stock + quantity
        val newStockCost = p.stockCost + total

        val purchaseId = dao.insertPurchase(Purchase(createdAt = date, productId = p.id, productName = p.name,
            quantity = quantity, unitCost = unit, total = total, supplierId = supplier?.id,
            supplierName = supplier?.name ?: "", paymentStatus = status, paidAmount = paidAmount, method = method,
            note = note.trim()))
        dao.updateProduct(p.copy(stock = newStock, stockCost = newStockCost, purchasePrice = Cost.avg(newStockCost, newStock, unit),
            salePrice = newSalePrice ?: p.salePrice, supplierId = supplier?.id ?: p.supplierId))
        dao.insertStockMove(StockMove(productId = p.id, productName = p.name, createdAt = date,
            type = StockType.PURCHASE, qtyChange = quantity, stockAfter = newStock, refId = purchaseId,
            note = "Mədaxil #$purchaseId"))
        if (paidAmount > 0) dao.insertCashTx(CashTransaction(createdAt = date, type = CashType.PURCHASE,
            method = method, amount = -paidAmount, refId = purchaseId, note = "Mədaxil #$purchaseId: ${p.name}"))
        if (debt > 0) dao.insertSupplierTx(SupplierTx(supplierId = supplier!!.id, createdAt = date,
            type = SupplierTxType.PURCHASE, amount = debt, purchaseId = purchaseId, note = "Mədaxil #$purchaseId: ${p.name}"))
        log("Mədaxil #$purchaseId", "${p.name}: $quantity ədəd × ${Money.formatAzn(unit)} = ${Money.formatAzn(total)}, " +
            "ödənildi: ${Money.formatAzn(paidAmount)}" + (supplier?.let { ", təchizatçı: ${it.name}" } ?: ""))
        purchaseId
    }

    suspend fun cancelPurchase(purchaseId: Long, reason: String) = db.withTransaction {
        val pu = dao.getPurchase(purchaseId) ?: throw PosException("Mədaxil tapılmadı")
        if (pu.cancelled) throw PosException("Bu mədaxil artıq ləğv edilib")
        val now = clock()
        val p = dao.getProduct(pu.productId)
        if (p != null) {
            if (p.stock < pu.quantity) throw PosException("Anbarda kifayət qədər məhsul yoxdur (qalıq: ${p.stock}). Məhsulun bir hissəsi artıq satılıb.")
            val after = p.stock - pu.quantity
            val cost = if (after == 0) 0L else (p.stockCost - pu.total).coerceAtLeast(0)
            dao.updateProduct(p.copy(stock = after, stockCost = cost, purchasePrice = Cost.avg(cost, after, p.purchasePrice)))
            dao.insertStockMove(StockMove(productId = p.id, productName = p.name, createdAt = now,
                type = StockType.PURCHASE_CANCEL, qtyChange = -pu.quantity, stockAfter = after, refId = purchaseId,
                note = "Mədaxil #$purchaseId ləğvi"))
        }
        if (pu.paidAmount > 0) dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.PURCHASE_CANCEL,
            method = pu.method, amount = pu.paidAmount, refId = purchaseId, note = "Mədaxil #$purchaseId ləğvi"))
        val debt = pu.total - pu.paidAmount
        if (debt > 0 && pu.supplierId != null) dao.insertSupplierTx(SupplierTx(supplierId = pu.supplierId,
            createdAt = now, type = SupplierTxType.CANCEL, amount = -debt, purchaseId = purchaseId,
            note = "Mədaxil #$purchaseId ləğvi"))
        dao.updatePurchase(pu.copy(cancelled = true, note = (pu.note + " [Ləğv: ${reason.trim()}]").trim()))
        log("Mədaxil #$purchaseId ləğv edildi", "${pu.productName}, ${pu.quantity} ədəd. Səbəb: $reason")
    }

    // ======================= MƏXARİC =======================

    suspend fun addExpense(amount: Long, category: String, description: String, method: String, date: Long, note: String): Long =
        db.withTransaction {
            if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
            val id = dao.insertExpense(Expense(createdAt = date, amount = amount, category = category,
                description = description.trim(), method = method, note = note.trim()))
            dao.insertCashTx(CashTransaction(createdAt = date, type = CashType.EXPENSE, method = method,
                amount = -amount, refId = id, note = "$category${if (description.isNotBlank()) ": $description" else ""}"))
            log("Məxaric #$id", "$category: ${Money.formatAzn(amount)} (${methodName(method)}). $description")
            id
        }

    suspend fun cancelExpense(id: Long) = db.withTransaction {
        val e = dao.getExpense(id) ?: throw PosException("Məxaric tapılmadı")
        if (e.cancelled) throw PosException("Bu məxaric artıq ləğv edilib")
        dao.updateExpense(e.copy(cancelled = true))
        dao.insertCashTx(CashTransaction(createdAt = clock(), type = CashType.EXPENSE_CANCEL, method = e.method,
            amount = e.amount, refId = id, note = "Məxaric #$id ləğvi"))
        log("Məxaric #$id ləğv edildi", "${e.category}: ${Money.formatAzn(e.amount)}")
    }

    // ======================= KASSA =======================

    suspend fun cashIn(amount: Long, method: String, note: String) = db.withTransaction {
        if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
        dao.insertCashTx(CashTransaction(createdAt = clock(), type = CashType.CASH_IN, method = method, amount = amount,
            note = note.trim().ifBlank { "Kassaya pul əlavə edildi" }))
        log("Kassa mədaxili", "${Money.formatAzn(amount)} (${methodName(method)}). $note")
    }

    suspend fun cashOut(amount: Long, method: String, note: String) = db.withTransaction {
        if (amount <= 0) throw PosException("Məbləğ 0-dan böyük olmalıdır")
        val bal = dao.cashBalanceBy(method)
        if (amount > bal) throw PosException("Kassada kifayət qədər vəsait yoxdur (${Money.format(bal)})")
        dao.insertCashTx(CashTransaction(createdAt = clock(), type = CashType.CASH_OUT, method = method, amount = -amount,
            note = note.trim().ifBlank { "Kassadan pul çıxarıldı" }))
        log("Kassa məxarici", "${Money.formatAzn(amount)} (${methodName(method)}). $note")
    }

    /** Kassanı açır. Sayılan nağd məbləğ sistemdəkindən fərqlidirsə, fərq düzəliş kimi yazılır. */
    suspend fun openCashSession(counted: Long, note: String) = db.withTransaction {
        if (dao.openSession() != null) throw PosException("Kassa artıq açıqdır")
        if (counted < 0) throw PosException("Məbləğ mənfi ola bilməz")
        val now = clock()
        val expected = dao.cashBalanceBy(PayMethod.CASH)
        if (counted != expected) {
            dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.ADJUSTMENT, method = PayMethod.CASH,
                amount = counted - expected, note = "Açılışda kassa düzəlişi"))
        }
        dao.insertSession(CashSession(openedAt = now, openingBalance = counted, note = note.trim()))
        log("Kassa açıldı", "Açılış balansı: ${Money.formatAzn(counted)}" +
            if (counted != expected) " (sistem: ${Money.formatAzn(expected)})" else "")
    }

    suspend fun closeCashSession(counted: Long, note: String) = db.withTransaction {
        val s = dao.openSession() ?: throw PosException("Açıq kassa yoxdur")
        if (counted < 0) throw PosException("Məbləğ mənfi ola bilməz")
        val now = clock()
        val expected = dao.cashBalanceBy(PayMethod.CASH)
        if (counted != expected) {
            dao.insertCashTx(CashTransaction(createdAt = now, type = CashType.ADJUSTMENT, method = PayMethod.CASH,
                amount = counted - expected, note = "Bağlanışda kassa fərqi"))
        }
        dao.updateSession(s.copy(closedAt = now, expectedBalance = expected, countedBalance = counted,
            note = listOf(s.note, note.trim()).filter { it.isNotBlank() }.joinToString(" / ")))
        log("Kassa bağlandı", "Gözlənilən: ${Money.formatAzn(expected)}, sayılan: ${Money.formatAzn(counted)}, " +
            "fərq: ${Money.formatAzn(counted - expected)}")
    }

    companion object {
        fun methodName(m: String?) = when (m) {
            PayMethod.CARD -> "Kart"
            PayMethod.CASH -> "Nağd"
            else -> "-"
        }
    }
}

/** Dəqiq maya dəyəri hesablamaları (hamısı qəpiklə, tam ədədlərlə). */
object Cost {
    /** Orta vahid qiymət (göstərmək üçün); stok 0-dırsa əvvəlki qiymət saxlanılır. */
    fun avg(cost: Long, qty: Int, fallback: Long): Long = if (qty > 0) (cost + qty / 2) / qty else fallback

    /**
     * Anbardan qty ədəd çıxanda onların dəqiq dəyəri. Son ədəd(lər) qalan dəyərin hamısını aparır,
     * buna görə yuvarlaqlaşdırma qalığı heç vaxt "itmir" — satılanların mayası + anbar dəyəri = alışların cəmi.
     */
    fun take(p: Product, qty: Int): Long = when {
        qty <= 0 -> 0
        qty >= p.stock -> p.stockCost
        else -> (p.stockCost * qty + p.stock / 2) / p.stock
    }

    /** Sayım/düzəlişdən sonra yeni dəyər: azalanda mütənasib çıxılır, artanda orta qiymətlə əlavə olunur. */
    fun afterRecount(p: Product, newStock: Int): Long = when {
        newStock == p.stock -> p.stockCost
        newStock < p.stock -> p.stockCost - take(p, p.stock - newStock)
        else -> p.stockCost + (newStock - p.stock).toLong() * (if (p.stock > 0) avg(p.stockCost, p.stock, p.purchasePrice) else p.purchasePrice)
    }
}
