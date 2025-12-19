import Foundation
import Combine

/**
 Code Snippet: MVVM + Manager Pattern
 Customer adding product to cart scenario.

 Demonstrates:
 - Repository implementation for data operations
 - Manager class for handling business logic
 - ViewModel for UI state management
 - Enums for modeling operation results and UI state
 */



/**
 @Product
 Product data model
 
@param id - productId, also used in CartDB Table
@param name - product name
@param inStock - boolean returns if product is in stock or not
@param maxQuantity - int value that describes the max quantity of the product
 */
struct Product {
    let id: String
    let name: String
    let inStock: Bool
    let maxQuantity: Int
}


/**
@CartRepository
Repository protocol for cart operations.

- Adding items to cart
- Querying quantity of a product
- Querying total items in cart
 */

protocol CartRepository {
    func add(productId: String, quantity: Int) async
    func getQuantity(productId: String) async -> Int
    func getTotalItems() async -> Int
}

/**
@CartRepositoryImpl
Concrete implementation of [CartRepository]
 */
class CartRepositoryImpl: CartRepository {
    private var cart: [String: Int] = [:]

    func add(productId: String, quantity: Int) async {
        let current = cart[productId] ?? 0
        cart[productId] = current + quantity
    }

    func getQuantity(productId: String) async -> Int {
        return cart[productId] ?? 0
    }

    func getTotalItems() async -> Int {
        return cart.values.reduce(0, +)
    }
}


/**
 @CartManager
 Handles cart business logic.
 - Checks if product exists
 - Checks product availability
 - Checks product max quantity
 - Updates repository
 */
class CartManager {
    let cartRepository: CartRepository
    let productRepository: ProductRepository

    init(cartRepository: CartRepository, productRepository: ProductRepository) {
        self.cartRepository = cartRepository
        self.productRepository = productRepository
    }

    /**
     Attempts to add a product to the cart.
     
     - Parameters:
        - productId: ID of the requested product
        - quantity: Quantity to add
     - Returns: [CartResult] indicating success or failure
     */
    func addToCart(productId: String, quantity: Int) async -> CartResult {
        guard let product = await productRepository.getProduct(productId) else {
            return .failure("Product not found")
        }

        if !product.inStock { return .failure("Out of stock") }

        let currentQty = await cartRepository.getQuantity(productId)
        if currentQty + quantity > product.maxQuantity {
            return .failure("Max quantity reached")
        }

        await cartRepository.add(productId: productId, quantity: quantity)
        let total = await cartRepository.getTotalItems()
        return .success(total)
    }
}

/**
 @CartViewModel
 ViewModel for Cart View.

 - Observes UI state via @Published `uiState`
 - Handles user events (e.g., adding item to cart)
 - Delegates business logic to [CartManager]
 */
class CartViewModel: ObservableObject {
    @Published var uiState: CartUiState = .idle
    private let cartManager: CartManager

    init(cartManager: CartManager) {
        self.cartManager = cartManager
    }

    func addItem(productId: String, quantity: Int) async {
        let result = await cartManager.addToCart(productId: productId, quantity: quantity)
        switch result {
        case .success(let totalItems):
            uiState = .itemAdded(totalItems)
        case .failure(let message):
            uiState = .error(message)
        }
    }
}

/**
 Represents the result of a cart operation.
 */
enum CartResult {
    case success(Int)      // total items in cart
    case failure(String)   // error message
}

/**
 Represents the UI state for the cart screen.
 */
enum CartUiState {
    case idle
    case itemAdded(Int)
    case error(String)
}
