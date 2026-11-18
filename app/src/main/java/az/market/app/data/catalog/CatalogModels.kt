package az.market.app.data.catalog

import az.market.app.core.common.Money
import az.market.app.data.remote.DecimalStringSerializer
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- DTOs (server JSON)

/** One row of public.catalog_products(): prices already computed by the server. */
@Serializable
data class ProductCardDto(
    val id: String,
    @SerialName("category_id") val categoryId: Long? = null,
    val name: String,
    val unit: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @Serializable(with = DecimalStringSerializer::class) val price: String,
    @Serializable(with = DecimalStringSerializer::class) @SerialName("final_price") val finalPrice: String,
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("in_stock") val inStock: Boolean = true,
    @SerialName("is_new") val isNew: Boolean = false,
)

@Serializable
data class BannerDto(
    val id: String,
    val title: String,
    val subtitle: String = "",
    @SerialName("image_url") val imageUrl: String,
    @SerialName("link_type") val linkType: String = "none",
    @SerialName("link_value") val linkValue: String? = null,
)

@Serializable
data class HomeCategoryDto(
    val id: Long,
    val name: String,
    val slug: String,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class BonusSummaryDto(
    @Serializable(with = DecimalStringSerializer::class) val balance: String,
    @SerialName("card_number") val cardNumber: String,
)

/** public.home_feed() */
@Serializable
data class HomeFeedDto(
    val banners: List<BannerDto> = emptyList(),
    val categories: List<HomeCategoryDto> = emptyList(),
    val discounted: List<ProductCardDto> = emptyList(),
    @SerialName("new_arrivals") val newArrivals: List<ProductCardDto> = emptyList(),
    @SerialName("best_sellers") val bestSellers: List<ProductCardDto> = emptyList(),
    @SerialName("for_you") val forYou: List<ProductCardDto> = emptyList(),
    val bonus: BonusSummaryDto? = null,
)

// ---------------------------------------------------------------- Domain

enum class ProductUnit(val server: String) {
    PIECE("piece"), KG("kg"), GRAM("gram"), LITER("liter"), ML("ml"), PACK("pack"), BOX("box");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.server == value } ?: PIECE
    }
}

data class ProductCard(
    val id: String,
    val categoryId: Long?,
    val name: String,
    val unit: ProductUnit,
    val imageUrl: String?,
    val price: Money,
    val finalPrice: Money,
    val discountPercent: Int,
    val inStock: Boolean,
    val isNew: Boolean,
) {
    val hasDiscount: Boolean get() = finalPrice < price
}

data class Banner(val id: String, val title: String, val subtitle: String, val imageUrl: String, val link: BannerLink)

sealed interface BannerLink {
    data object None : BannerLink
    data class Product(val productId: String) : BannerLink
    data class Category(val categoryId: Long) : BannerLink
    data class Promotion(val promotionId: String) : BannerLink
    data class Url(val url: String) : BannerLink
}

data class HomeCategory(val id: Long, val name: String, val imageUrl: String?)

data class BonusSummary(val balance: Money, val cardNumber: String)

data class HomeFeed(
    val banners: List<Banner>,
    val categories: List<HomeCategory>,
    val discounted: List<ProductCard>,
    val newArrivals: List<ProductCard>,
    val bestSellers: List<ProductCard>,
    val forYou: List<ProductCard>,
    val bonus: BonusSummary?,
) {
    val hasProducts: Boolean get() = (discounted + newArrivals + bestSellers + forYou).isNotEmpty()
}

/** Server returns numeric(12,2); tolerate "5", "5.0", "5.00". */
internal fun moneyOf(value: String): Money =
    Money.fromDecimalString(BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())

fun ProductCardDto.toDomain() = ProductCard(
    id, categoryId, name, ProductUnit.from(unit), imageUrl, moneyOf(price), moneyOf(finalPrice), discountPercent, inStock, isNew,
)

fun BannerDto.toDomain() = Banner(
    id, title, subtitle, imageUrl,
    when (linkType) {
        "product" -> linkValue?.let { BannerLink.Product(it) }
        "category" -> linkValue?.toLongOrNull()?.let { BannerLink.Category(it) }
        "promotion" -> linkValue?.let { BannerLink.Promotion(it) }
        "url" -> linkValue?.takeIf { it.startsWith("https://") }?.let { BannerLink.Url(it) } // only https links
        else -> null
    } ?: BannerLink.None,
)

fun HomeFeedDto.toDomain() = HomeFeed(
    banners = banners.map { it.toDomain() },
    categories = categories.map { HomeCategory(it.id, it.name, it.imageUrl ?: it.iconUrl) },
    discounted = discounted.map { it.toDomain() },
    newArrivals = newArrivals.map { it.toDomain() },
    bestSellers = bestSellers.map { it.toDomain() },
    forYou = forYou.map { it.toDomain() },
    bonus = bonus?.let { BonusSummary(moneyOf(it.balance), it.cardNumber) },
)

enum class ProductSort(val server: String) { POPULAR("popular"), NEW("new"), DISCOUNT("discount"), PRICE_ASC("price_asc"), PRICE_DESC("price_desc") }
