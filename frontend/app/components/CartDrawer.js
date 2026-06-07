'use client';

// CartDrawer.js
// Panel ตะกร้าสินค้าที่เลื่อนออกมาจากขวา
// เปิด/ปิดผ่าน CartContext.isOpen

import { useRouter } from 'next/navigation';
import { useCart } from '../CartContext';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function CartDrawer() {
  const router = useRouter();
  const {
    items,
    cartTotal,
    isOpen,
    closeCart,
    removeFromCart,
    updateQuantity,
    clearCart,
  } = useCart();

  return (
    <>
      {/* ── Overlay (พื้นหลังมืด) ───────────────────────────── */}
      <div
        onClick={closeCart}
        className={`
          fixed inset-0 bg-black/40 z-40
          transition-opacity duration-300
          ${isOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'}
        `}
      />

      {/* ── Drawer ──────────────────────────────────────────── */}
      <div
        className={`
          fixed top-0 right-0 h-full w-full max-w-md bg-white z-50 shadow-2xl
          flex flex-col
          transition-transform duration-300 ease-in-out
          ${isOpen ? 'translate-x-0' : 'translate-x-full'}
        `}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b bg-[#0B1F33]">
          <div className="flex items-center gap-2">
            <span className="text-xl">🛒</span>
            <h2 className="text-white font-semibold text-lg">ตะกร้าสินค้า</h2>
            {items.length > 0 && (
              <span className="bg-[#A8CEFF] text-[#0B1F33] text-xs font-bold px-2 py-0.5 rounded-full">
                {items.length} รายการ
              </span>
            )}
          </div>
          <button
            onClick={closeCart}
            className="text-gray-300 hover:text-white text-2xl leading-none transition-colors"
          >
            ✕
          </button>
        </div>

        {/* ── รายการสินค้า ──────────────────────────────────── */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">

          {items.length === 0 ? (
            /* Empty state */
            <div className="flex flex-col items-center justify-center h-full text-center py-20">
              <div className="text-7xl mb-4 opacity-30">🛒</div>
              <p className="text-gray-500 font-medium text-lg">ตะกร้าว่างเปล่า</p>
              <p className="text-gray-400 text-sm mt-1">เพิ่มสินค้าจากหน้าสินค้าก่อนนะ</p>
              <button
                onClick={closeCart}
                className="mt-6 px-6 py-2 bg-[#0B1F33] text-[#A8CEFF] rounded-lg text-sm font-semibold hover:bg-blue-900 transition"
              >
                ← กลับไปดูสินค้า
              </button>
            </div>
          ) : (
            items.map(({ product, quantity }) => (
              <div
                key={product.id}
                className="flex gap-3 bg-gray-50 rounded-xl p-3 border border-gray-100"
              >
                {/* รูปสินค้า */}
                <div className="w-20 h-20 flex-shrink-0 rounded-lg overflow-hidden bg-gray-200">
                  {product.imageUrl ? (
                    <img
                      src={`${API_URL}/${product.imageUrl}`}
                      alt={product.name}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-2xl">
                      🍞
                    </div>
                  )}
                </div>

                {/* ข้อมูลสินค้า */}
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-gray-800 truncate">{product.name}</p>
                  <p className="text-xs text-gray-400 mb-2">{product.category}</p>

                  {/* ราคา + ปุ่มปรับจำนวน */}
                  <div className="flex items-center justify-between">
                    <span className="text-blue-600 font-bold text-sm">
                      ฿{(product.price * quantity).toLocaleString()}
                    </span>

                    {/* ปุ่ม − จำนวน + */}
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => updateQuantity(product.id, quantity - 1)}
                        className="w-7 h-7 rounded-full bg-gray-200 hover:bg-gray-300 text-gray-700 font-bold flex items-center justify-center transition"
                      >
                        −
                      </button>
                      <span className="w-8 text-center text-sm font-semibold">{quantity}</span>
                      <button
                        onClick={() => updateQuantity(product.id, quantity + 1)}
                        disabled={quantity >= product.stock}
                        className={`w-7 h-7 rounded-full font-bold flex items-center justify-center transition
                          ${quantity >= product.stock
                            ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
                            : 'bg-[#0B1F33] hover:bg-blue-900 text-[#A8CEFF]'}`}
                      >
                        +
                      </button>
                    </div>
                  </div>
                </div>

                {/* ปุ่มลบ */}
                <button
                  onClick={() => removeFromCart(product.id)}
                  className="text-gray-300 hover:text-red-400 transition text-lg leading-none self-start mt-1"
                  title="นำออก"
                >
                  ✕
                </button>
              </div>
            ))
          )}
        </div>

        {/* ── Footer: ราคารวม + ปุ่มสั่งซื้อ ──────────────── */}
        {items.length > 0 && (
          <div className="border-t px-5 py-4 space-y-3 bg-white">

            {/* สรุปยอด */}
            <div className="flex justify-between items-center text-sm text-gray-500">
              <span>รวม {items.reduce((s, i) => s + i.quantity, 0)} ชิ้น</span>
              <button
                onClick={clearCart}
                className="text-red-400 hover:text-red-500 text-xs underline"
              >
                ล้างตะกร้า
              </button>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-gray-600 font-medium">ราคารวม</span>
              <span className="text-2xl font-bold text-[#0B1F33]">
                ฿{cartTotal.toLocaleString()}
              </span>
            </div>

            {/* ปุ่มดำเนินการสั่งซื้อ */}
            <button
              className="w-full py-3 bg-[#0B1F33] text-[#A8CEFF] font-bold rounded-xl hover:bg-blue-900 transition text-base"
              onClick={() => {
                closeCart();
                router.push('/checkout');
              }}
            >
              ดำเนินการสั่งซื้อ →
            </button>
          </div>
        )}
      </div>
    </>
  );
}
