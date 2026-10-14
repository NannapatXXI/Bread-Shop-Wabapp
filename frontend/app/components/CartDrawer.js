'use client';

// CartDrawer.js
// Panel ตะกร้าสินค้าที่เลื่อนออกมาจากขวา (มือถือเป็น bottom sheet)
// เปิด/ปิดผ่าน CartContext.isOpen

import { useRouter } from 'next/navigation';
import { useCart } from '../CartContext';
import Icon from './Icon';
import { CATEGORY_LABEL } from './ProductCard';

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

  const totalQty = items.reduce((s, i) => s + i.quantity, 0);

  return (
    <>
      <div className={`scrim${isOpen ? ' open' : ''}`} onClick={closeCart} />

      <aside className={`drawer${isOpen ? ' open' : ''}`} role="dialog" aria-label="ตะกร้าสินค้า" aria-hidden={!isOpen}>
        <div className="drawer-head">
          <h2>
            ตะกร้าของฉัน{' '}
            {items.length > 0 && (
              <span className="muted" style={{ fontFamily: 'var(--font)', fontWeight: 400, fontSize: 15 }}>({totalQty} ชิ้น)</span>
            )}
          </h2>
          <button className="icon-btn" onClick={closeCart} aria-label="ปิด"><Icon name="x" /></button>
        </div>

        <div className="drawer-body">
          {items.length === 0 ? (
            <div className="empty" style={{ border: 0, marginTop: 24 }}>
              <div className="ic"><Icon name="cart" /></div>
              <h3>ตะกร้าว่างเปล่า</h3>
              <p>เพิ่มสินค้าจากหน้าสินค้าก่อนนะ</p>
              <button className="btn btn-outline" onClick={closeCart}>กลับไปดูสินค้า</button>
            </div>
          ) : (
            items.map(({ product, quantity }) => (
              <div className="line-item" key={product.id}>
                {product.imageUrl
                  ? <img src={`${API_URL}/${product.imageUrl}`} alt={product.name} />
                  : <div style={{ width: 72, height: 72, borderRadius: 'var(--r-md)', background: '#efe7dc', flexShrink: 0 }} />}
                <div className="info">
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <h3>{product.name}</h3>
                    <button className="icon-btn sm" onClick={() => removeFromCart(product.id)} aria-label={`ลบ ${product.name}`}>
                      <Icon name="trash" size="sm" />
                    </button>
                  </div>
                  <p className="muted" style={{ margin: 0, fontSize: 13 }}>
                    {CATEGORY_LABEL[product.category] ?? product.category} · ฿{Number(product.price).toLocaleString()}/ชิ้น
                  </p>
                  <div className="bottom">
                    <div className="qty">
                      <button onClick={() => updateQuantity(product.id, quantity - 1)} aria-label="ลด"><Icon name="minus" size="sm" /></button>
                      <span>{quantity}</span>
                      <button onClick={() => updateQuantity(product.id, quantity + 1)} disabled={quantity >= product.stock}
                        aria-label="เพิ่ม" style={quantity >= product.stock ? { color: 'var(--faint)', cursor: 'not-allowed' } : undefined}>
                        <Icon name="plus" size="sm" />
                      </button>
                    </div>
                    <b className="head num">฿{(product.price * quantity).toLocaleString()}</b>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        {items.length > 0 && (
          <div className="drawer-foot">
            <div className="rows">
              <div className="r"><span className="muted">ราคาสินค้า</span><span className="num">฿{cartTotal.toLocaleString()}</span></div>
              <div className="r"><span className="muted">ค่าจัดส่ง</span><span>คำนวณตอนชำระเงิน</span></div>
              <div className="r total"><span>รวม</span><b className="num">฿{cartTotal.toLocaleString()}</b></div>
            </div>
            <button className="btn btn-primary btn-block" onClick={() => { closeCart(); router.push('/checkout'); }}>
              ไปชำระเงิน <Icon name="right" size="sm" />
            </button>
            <button className="btn btn-ghost sm" style={{ alignSelf: 'center' }} onClick={clearCart}>ล้างตะกร้า</button>
          </div>
        )}
      </aside>
    </>
  );
}
