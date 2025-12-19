package com.example.codesnippets

/**
 *  Code Snippet - MVVM + Manager
 *  Customer adding product to cart
 *
 *  Demonstrates:
 * - Data Class
 * - Dao and Repository implementation for database operations
 * - Manager class for handling business logic
 * - ViewModel for UI state management
 * - Sealed classes for modeling operation results and UI state
 * */


/**
 * @Product
 * Product data model
 *
 * @param id - productId, also used in CartDB Table
 * @param name - product name
 * @param inStock - boolean returns if product is in stock or not
 * @param maxQuantity - int value that describes the max quantity of the product
 * */
data class Product(
    var id: String, // UUID could be used here
    var name: String,
    var inStock: Boolean,
    var maxQuantity: Int
)

/**
 *  @CartDao
 *  Dao(Data Access Object) for Cart table.
 *
 * - Adding items to cart
 * - Querying quantity of a product
 * - Querying total items in cart
 */
@Dao
interface CartDao {
    /**
     * Inserts or updates a cart item with the given quantity.
     *
     * @param productId The product ID to add
     * @param quantity Quantity of product added to cart
     * */
    @Insert(onConflict = OnConflictStrategy.Replace)
    suspend fun addItemToCart(productId: string, quantity: Int)

    /**
     * Returns the quantity of the specified productId
     *
     * @param productId the id of the required product that is in the cart
     * @return quantity of productId in the cart
     * */
    @Query("SELECT quantity FROM Cart WHERE productId = :productId")
    suspend fun getQuantity(productId: String): Int

    /**
     * Returns the count of total items in cart
     *
     * @return count of items in cart
     */
    @Query("SELECT COUNT(*) FROM Cart")
    suspend fun getTotalItems(): Int
}

/**
 * @CartRepository
 * Repository interface for cart operations.
 *
 * - add item to cart
 * - get quantity of product in cart
 * - get total items in cart
 **/
interface CartRepository {
    suspend fun add(productId: String, quantity: Int)
    suspend fun getQuantity(productId: String): Int
    suspend fun getTotalItems(): Int
}

/**
 * @CartRepositoryImpl
 * Concrete implementation of [CartRepository] using [CartDao].
 *
 * Provides Room-based persistence.
 */
class CartRepositoryImpl(private val cartDao: CartDao): CartRepository {

    override suspend fun getQuantity(productId: String): Int {
        return cartDao.getQuantity(productId)
    }

    override suspend fun getTotalItems(): Int {
        return cartDao.getTotalItems()
    }

    override suspend fun add(productId: String, quantity: Int) {
        cartDao.addItemToCart(productId, quantity)
    }
}

/**
 * @CartManager
 * Handles cart business logic.
 *
 * - Checks if product exists
 * - Checks product availability
 * - Checks product max quantity
 * - Repository updates
 *
 * @property cartRepository Repository to read/write cart data
 * @property productRepository Repository to read product info
 */
class CartManager(
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository
) {

    /**
     * Attempts to add a product to the cart.
     *
     * @param productId id of the requested product
     * @param quantity quantity to add
     * @return [CartResult] indicating success or failure
     */
    suspend fun addToCart(productId: String, quantity: Int): CartResult {
        val product = productRepository.getProduct(productId) ?: return CartResult.Error("Not found")
        if (!product.inStock) return CartResult.Error("Out of stock")
        if (quantity + cartRepository.getQuantity(productId) > product.maxQuantity)
            return CartResult.Error("Max quantity reached")

        cartRepository.add(productId, quantity)
        return CartResult.Success(cartRepository.getTotalItems())
    }
}

/**
 * @CartViewModel
 * ViewModel for Cart View
 *
 * - Observes UI state via [uiState]
 * - Handles user events (e.g., adding item to cart)
 * - Delegates business logic to [CartManager]
 *
 * */

class CartViewModel(
    private val cartManager: CartManager
) : ViewModel() {

    val uiState = MutableStateFlow<CartUiState>(CartUiState.Idle)

    fun addItem(productId: String, quantity: Int) {
        viewModelScope.launch {
            val result = cartManager.addToCart(productId, quantity)
            uiState.value = when(result) {
                is CartResult.Success -> CartUiState.ItemAdded(result.totalItems)
                is CartResult.Error -> CartUiState.Error(result.message)
            }
        }
    }
}

/**
 * @CartResult
 * Represents the result of a cart operation.
 */
sealed class CartResult {
    data class Success(val totalItems: Int) : CartResult()
    data class Error(val message: String) : CartResult()
}

/**
 * @CartUiState
 * Represents the UI state for the cart screen.
 */
sealed class CartUiState {
    object Idle : CartUiState()
    data class ItemAdded(val totalItems: Int) : CartUiState()
    data class Error(val message: String) : CartUiState()
}
