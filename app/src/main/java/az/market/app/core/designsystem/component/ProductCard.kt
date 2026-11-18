package az.market.app.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import az.market.app.R
import az.market.app.core.designsystem.theme.LocalMarketColors
import az.market.app.core.designsystem.theme.PriceTextStyles
import az.market.app.core.designsystem.theme.Spacing
import az.market.app.data.catalog.ProductCard
import az.market.app.data.catalog.ProductUnit

@StringRes
fun ProductUnit.label(): Int = when (this) {
    ProductUnit.PIECE -> R.string.unit_piece
    ProductUnit.KG -> R.string.unit_kg
    ProductUnit.GRAM -> R.string.unit_gram
    ProductUnit.LITER -> R.string.unit_liter
    ProductUnit.ML -> R.string.unit_ml
    ProductUnit.PACK -> R.string.unit_pack
    ProductUnit.BOX -> R.string.unit_box
}

/** Product image with a neutral placeholder while loading / when the product has no photo. */
@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ShoppingBasket, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
        }
    }
    if (url.isNullOrBlank()) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = modifier,
        )
    }
}

@Composable
fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        color = LocalMarketColors.current.discount,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.badge_discount, percent),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Final price (server-computed) with the old price struck through when discounted. */
@Composable
fun PriceBlock(product: ProductCard) {
    Column {
        if (product.hasDiscount) {
            Text(
                product.price.format(),
                style = PriceTextStyles.Old,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                product.finalPrice.format(),
                style = PriceTextStyles.Current,
                color = if (product.hasDiscount) LocalMarketColors.current.discount else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                " " + stringResource(R.string.price_per_unit, stringResource(product.unit.label())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProductCardItem(product: ProductCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.testTag("product_${product.id}"),
    ) {
        Box {
            ProductImage(
                product.imageUrl,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .alpha(if (product.inStock) 1f else 0.45f),
            )
            Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (product.discountPercent > 0) DiscountBadge(product.discountPercent)
                if (product.isNew) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(R.string.badge_new),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (!product.inStock) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        stringResource(R.string.out_of_stock),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(
                product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PriceBlock(product)
        }
    }
}

/** Fixed-width card used in horizontal home sections. */
@Composable
fun ProductCardSmall(product: ProductCard, onClick: () -> Unit) {
    ProductCardItem(product, onClick, Modifier.width(156.dp))
}

/** Skeleton while loading. */
@Composable
fun ProductCardPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(0.5f).height(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
