import Icon from './Icon';

export const CATEGORY_LABEL = { BREAD: 'ขนมปัง', CAKE: 'เค้ก', COOKIE: 'คุกกี้', DRINK: 'เครื่องดื่ม' };

export default function ProductCard({ product, imageSrc, inCart, onAdd, showStock = false }) {
  const out = product.stock === 0;
  const maxed = out || inCart >= product.stock;
  const lowStock = !out && product.stock <= 5;
  const addLabel = out ? `${product.name} หมดสต็อก`
    : maxed ? `${product.name} ครบจำนวนในสต็อกแล้ว`
    : `เพิ่ม ${product.name} ลงตะกร้า`;

  return (
    <article className={`pcard${out ? ' out' : ''}`}>
      <div className="ph">
        {product.imageUrl
          ? <img src={imageSrc} alt={product.name} />
          : <span className="ph-empty">ไม่มีรูปสินค้า</span>}
        {out && <span className="tag">หมดแล้ว</span>}
        {!out && maxed && <span className="tag">ครบแล้ว</span>}
      </div>
      <div className="pmeta">
        <div style={{ minWidth: 0 }}>
          <h3>{product.name}</h3>
          {product.description && <p className="desc">{product.description}</p>}
          <div className="price">
            ฿{Number(product.price).toLocaleString()}
            {showStock && !out && (
              <span className={`stock${lowStock ? ' low' : ''}`}>
                {lowStock ? `เหลือ ${product.stock} ชิ้น` : `มี ${product.stock} ชิ้น`}
              </span>
            )}
          </div>
        </div>
        <button className="add" disabled={maxed} onClick={() => !maxed && onAdd(product)}
          aria-label={addLabel} title={addLabel}>
          <Icon name="plus" />
        </button>
      </div>
    </article>
  );
}
