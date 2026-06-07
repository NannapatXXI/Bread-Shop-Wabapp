// app/(user)/product/page.js
'use client';

// [Claude] หน้าแสดงสินค้าสำหรับ user — search ด้วย Boyer-Moore, filter ตาม category

import { useState, useEffect } from 'react';
import { useCart } from '../../CartContext';
import { FaSearch } from 'react-icons/fa';
import { AiFillProduct } from "react-icons/ai";
import { getProducts } from "@/services/auth.service";
import { boyerMooreContains } from "@/lib/boyerMoore"; // [Claude] Boyer-Moore search

export default function ProductPage() {

  const { addToCart, items } = useCart();

  // หาจำนวนสินค้าที่อยู่ในตะกร้าแล้ว (ใช้เช็คกับ stock)
  const getCartQty = (productId) =>
    items.find(i => i.product.id === productId)?.quantity ?? 0;
  const [products, setProducts]         = useState([]);
  const [search, setSearch]             = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL'); // [Claude] filter หมวดหมู่

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    try {
      const res = await getProducts();
      setProducts(res.data);
    } catch (err) {
      if (err.response?.status !== 403) {
        console.error(err);
      }
    }
  };

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
    <div className="bg-[#EEF4FB] rounded-lg p-6 md:p-8">

      {/* Header */}
      <div className="mb-6">
        <h2 className="text-2xl font-bold text-gray-800">Product</h2>
        <p className="text-gray-500">สินค้าทั้งหมด {filteredProducts.length} รายการ</p>
      </div>

      {/* Search + Category Filter */}
      <div className="flex items-center justify-between gap-4 mb-6 bg-white shadow-md p-2 rounded-lg">

        <div className="inline-flex p-1 rounded-lg">
          <div className="grid grid-cols-[150px_10px_150px_150px_150px_150px] gap-2">

            <button
              onClick={() => handleCategoryChange('ALL')}
              className={`px-4 py-2 rounded-md font-semibold transition-all flex items-center justify-center gap-1
                ${categoryFilter === 'ALL' ? 'bg-[#0B1F33] text-[#A8CEFF]' : 'text-gray-400 hover:bg-gray-200'}`}>
              <AiFillProduct /> All
            </button>

            <div className="text-2xl text-gray-400 flex items-center justify-center">|</div>

            <button
              onClick={() => handleCategoryChange('BREAD')}
              className={`px-4 py-2 rounded-md font-semibold w-full transition-all
                ${categoryFilter === 'BREAD' ? 'bg-[#0B1F33] text-[#A8CEFF]' : 'text-gray-400 hover:bg-gray-200'}`}>
              Bread
            </button>

            <button
              onClick={() => handleCategoryChange('CAKE')}
              className={`px-4 py-2 rounded-md font-semibold w-full transition-all
                ${categoryFilter === 'CAKE' ? 'bg-[#0B1F33] text-[#A8CEFF]' : 'text-gray-400 hover:bg-gray-200'}`}>
              Cake
            </button>

            <button
              onClick={() => handleCategoryChange('COOKIE')}
              className={`px-4 py-2 rounded-md font-semibold w-full transition-all
                ${categoryFilter === 'COOKIE' ? 'bg-[#0B1F33] text-[#A8CEFF]' : 'text-gray-400 hover:bg-gray-200'}`}>
              Cookie
            </button>

            <button
              onClick={() => handleCategoryChange('DRINK')}
              className={`px-4 py-2 rounded-md font-semibold w-full transition-all
                ${categoryFilter === 'DRINK' ? 'bg-[#0B1F33] text-[#A8CEFF]' : 'text-gray-400 hover:bg-gray-200'}`}>
              Drink
            </button>
          </div>
        </div>

        {/* Search Box */}
        <div className="relative flex-1">
          <FaSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="ค้นหาสินค้า..."
            value={search}
            onChange={handleSearchChange}
            className="w-full pl-10 pr-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-[#E0EBF8]"
          />
        </div>
      </div>

      {/* Product Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {filteredProducts.length === 0 ? (
          <div className="col-span-full text-center py-12">
            <div className="text-6xl mb-4 opacity-50">📦</div>
            <h3 className="text-xl font-semibold text-gray-600">
              {search ? `ไม่พบสินค้า "${search}"` : 'ยังไม่มีสินค้า'}
            </h3>
            <p className="text-gray-400">
              {search ? 'ลองค้นหาด้วยคำอื่น' : 'ไม่มีสินค้าในหมวดหมู่นี้'}
            </p>
          </div>
        ) : (
          filteredProducts.map((product) => (
            <div key={product.id}
                 className="bg-white rounded-lg shadow border overflow-hidden transition-all duration-300 hover:shadow-xl hover:-translate-y-1">

              {/* รูปสินค้า — [Claude] เช็ค null ก่อน render ป้องกัน /null error */}
              <div className="h-48 flex items-center justify-center bg-gray-100">
                {product.imageUrl
                  ? <img src={`http://localhost:8080/${product.imageUrl}`} alt={product.name} className="h-full w-full object-cover" />
                  : <div className="text-gray-300 text-sm">No image</div>
                }
              </div>

              <div className="p-4">
                <h3 className="text-lg font-bold text-gray-800 truncate">{product.name}</h3>
                <p className="text-sm text-gray-500 h-10 overflow-hidden">{product.description}</p>
                <div className="border-t border-gray-100 my-2" />
                <div className="flex justify-between items-center my-3">
                  <span className="text-2xl font-bold text-blue-600">฿{product.price.toLocaleString()}</span>
                  <span className="text-xs font-medium bg-gray-100 text-gray-600 px-2 py-1 rounded-full">
                    คงเหลือ {product.stock}
                  </span>
                </div>

                {(() => {
                  const inCart  = getCartQty(product.id);
                  const maxed   = product.stock === 0 || inCart >= product.stock;
                  return (
                    <button
                      onClick={() => !maxed && handleSelectProduct(product)}
                      disabled={maxed}
                      className={`w-full px-3 py-2 font-bold rounded-md transition
                        ${maxed
                          ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
                          : 'bg-[#0B1F33] text-[#A8CEFF] hover:bg-blue-700'}`}
                    >
                      {product.stock === 0
                        ? 'หมดสต็อก'
                        : inCart >= product.stock
                          ? 'ครบแล้ว'
                          : inCart > 0
                            ? '+ เพิ่ม'
                            : 'เลือกซื้อ'}
                    </button>
                  );
                })()}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
