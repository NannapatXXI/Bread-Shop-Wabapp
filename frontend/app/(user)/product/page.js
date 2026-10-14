// app/(user)/product/page.js
'use client';

// [Claude] หน้าแสดงสินค้าสำหรับ user — search ด้วย Boyer-Moore, filter ตาม category

import { useState, useEffect } from 'react';
import { useCart } from '../../CartContext';
import { getProducts } from "@/services/auth.service";
import { boyerMooreContains } from "@/lib/boyerMoore"; // [Claude] Boyer-Moore search
import Icon from '../../components/Icon';
import ProductCard, { CATEGORY_LABEL } from '../../components/ProductCard';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function ProductPage() {

  const { addToCart, items } = useCart();

  // หาจำนวนสินค้าที่อยู่ในตะกร้าแล้ว (ใช้เช็คกับ stock)
  const getCartQty = (productId) =>
    items.find(i => i.product.id === productId)?.quantity ?? 0;
  const [products, setProducts]         = useState([]);
  const [search, setSearch]             = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL'); // [Claude] filter หมวดหมู่

  useEffect(() => {
    getProducts()
      .then(res => setProducts(res.data))
      .catch(err => {
        if (err.response?.status !== 403) {
          console.error(err);
        }
      });
  }, []);

  // [Claude] Boyer-Moore search + category filter
  const filteredProducts = products.filter(p => {
    const matchCategory = categoryFilter === 'ALL' || p.category === categoryFilter;
    const matchSearch   = boyerMooreContains(p.name ?? '', search);
    return matchCategory && matchSearch;
  });

  // reset หน้า 1 เมื่อ filter เปลี่ยน
  const handleCategoryChange = (cat) => setCategoryFilter(cat);
  const handleSearchChange   = (e)   => setSearch(e.target.value);

  const handleSelectProduct = (product) => {
    addToCart(product);
  };

  return (
    <>
      <div className="page-head">
        <div><h1>สินค้าทั้งหมด</h1><p>{filteredProducts.length} รายการ</p></div>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', marginBottom: 24, flexWrap: 'wrap' }}>
        <div className="chips scrollbar-none" role="tablist" style={{ minWidth: 0, maxWidth: '100%' }}>
          {['ALL', 'BREAD', 'CAKE', 'COOKIE', 'DRINK'].map(cat => (
            <button key={cat} role="tab" aria-selected={categoryFilter === cat}
              className={`chip${categoryFilter === cat ? ' on' : ''}`} onClick={() => handleCategoryChange(cat)}>
              {cat === 'ALL' ? 'ทั้งหมด' : CATEGORY_LABEL[cat]}
            </button>
          ))}
        </div>
        <label className="search" style={{ background: 'var(--surface)', flex: '0 1 280px' }}>
          <Icon name="search" size="sm" />
          <input placeholder="ค้นหาสินค้า" aria-label="ค้นหาสินค้า" value={search} onChange={handleSearchChange} />
        </label>
      </div>

      {filteredProducts.length === 0 ? (
        <div className="empty">
          <div className="ic"><Icon name="box" /></div>
          <h3>{search ? `ไม่พบสินค้า "${search}"` : 'ยังไม่มีสินค้า'}</h3>
          <p>{search ? 'ลองค้นหาด้วยคำอื่น' : 'ไม่มีสินค้าในหมวดหมู่นี้'}</p>
        </div>
      ) : (
        <div className="pgrid">
          {filteredProducts.map((product) => (
            <ProductCard key={product.id} product={product} showStock
              imageSrc={`${API_URL}/${product.imageUrl}`}
              inCart={getCartQty(product.id)} onAdd={handleSelectProduct} />
          ))}
        </div>
      )}
    </>
  );
}
