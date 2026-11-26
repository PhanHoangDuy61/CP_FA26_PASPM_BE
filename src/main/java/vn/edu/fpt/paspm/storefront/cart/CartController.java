package vn.edu.fpt.paspm.storefront.cart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.AddBundle;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.AddCartItem;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.BundleAddRejected;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.BundleAddResult;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.BundleConflict;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.CartView;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.ChangeCartItemQuantity;

@RestController
@RequestMapping("/api/storefront/{storeSlug}/cart")
public class CartController {
    private static final Pattern VERSION = Pattern.compile("\"(0|[1-9][0-9]{0,18})\"");
    private static final String VERSION_DESCRIPTION = "Strong quoted cart version, e.g. \"0\" for a virtual cart";

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    @Operation(operationId = "getStorefrontCart", summary = "Read the customer's cart in an ACTIVE Store")
    public CartView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug) {
        return carts.get(storeSlug, customerId(jwt));
    }

    @PostMapping("/items")
    @Operation(operationId = "addStorefrontCartItem", summary = "Add a Product or increase its quantity in the cart")
    public CartView add(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug,
            @Parameter(description = VERSION_DESCRIPTION) @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AddCartItem request) {
        return carts.add(storeSlug, customerId(jwt), expectedVersion(ifMatch), request.productId(), request.quantity());
    }

    @PostMapping("/bundles")
    @Operation(operationId = "addStorefrontCartBundle", summary = "Add one active Layout B bundle to the cart")
    @ApiResponse(responseCode = "200", description = "At least one component was added",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BundleAddResult.class)))
    @ApiResponse(responseCode = "409", description = "Stale cart, unavailable bundle or no component added",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(oneOf = {BundleAddRejected.class, BundleConflict.class})))
    public BundleAddResult addBundle(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug,
            @Parameter(description = VERSION_DESCRIPTION) @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AddBundle request) {
        return carts.addBundle(storeSlug, customerId(jwt), expectedVersion(ifMatch), request.bundleKey());
    }

    @ExceptionHandler(CartService.BundleRejected.class)
    public ResponseEntity<BundleAddRejected> bundleRejected(CartService.BundleRejected exception) {
        return ResponseEntity.status(exception.status()).body(new BundleAddRejected(
                exception.getMessage(), exception.code(), exception.components()));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(operationId = "changeStorefrontCartItemQuantity", summary = "Set the quantity of a customer's cart line")
    public CartView changeQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug,
            @PathVariable long itemId,
            @Parameter(description = VERSION_DESCRIPTION) @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ChangeCartItemQuantity request) {
        return carts.changeQuantity(storeSlug, customerId(jwt), expectedVersion(ifMatch), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(operationId = "removeStorefrontCartItem", summary = "Remove one line from the customer's cart")
    public CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug,
            @PathVariable long itemId,
            @Parameter(description = VERSION_DESCRIPTION) @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return carts.remove(storeSlug, customerId(jwt), expectedVersion(ifMatch), itemId);
    }

    @DeleteMapping
    @Operation(operationId = "clearStorefrontCart", summary = "Clear the customer's cart while retaining its identity")
    public CartView clear(@AuthenticationPrincipal Jwt jwt, @PathVariable String storeSlug,
            @Parameter(description = VERSION_DESCRIPTION) @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return carts.clear(storeSlug, customerId(jwt), expectedVersion(ifMatch));
    }

    private static int customerId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

    private static long expectedVersion(String ifMatch) {
        var match = VERSION.matcher(ifMatch);
        if (match.matches()) {
            try {
                return Long.parseLong(match.group(1));
            } catch (NumberFormatException ignored) {
                // A syntactically valid tag may still overflow a signed 64-bit version.
            }
        }
        throw new ApiException(400, "If-Match must contain one strong quoted cart version");
    }
}
