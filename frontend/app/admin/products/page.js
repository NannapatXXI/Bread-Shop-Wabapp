// app/(app)/home/product/page.js
'use client'; 

// 1. Import เครื่องมือที่จำเป็น
import { useState,useRef } from 'react';
import { useEffect } from 'react';
import toast from 'react-hot-toast'; // (เราจะใช้ toast แจ้งเตือน)
import Icon from '../../components/Icon';
import { CATEGORY_LABEL } from '../../components/ProductCard';

import { getproduct,addproduct } from "@/services/auth.service";
import api from "@/lib/api"; // [Claude] ใช้เรียก delete API
import { boyerMooreContains } from "@/lib/boyerMoore"; // [Claude] Boyer-Moore search
import Cropper from "react-easy-crop";
import { useRouter } from "next/navigation";

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';





// (เริ่ม Component - เหมือนเดิม)
export default function ProductPage() {

  // (States ทั้งหมด - เหมือนเดิม)
  const router = useRouter();

  const fileInputRef = useRef(null);
  const [imageSrc, setImageSrc] = useState(null);     // รูปดิบก่อน crop
  const [imageFile, setImageFile] = useState(null);   // รูปหลัง crop (final)
  const [showCrop, setShowCrop] = useState(false);    // เปิด modal crop
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);

  const [showPreview, setShowPreview] = useState(false); 
  const [showPreviewOfAddProduct, setShowPreviewOfAddProduct] = useState(false);
  
  const [showEditProduct, setshowEditProduct] = useState(false); 

  const [products, setProducts] = useState([]);

  // [Claude] state สำหรับ confirm modal ลบสินค้า
  const [deleteModal, setDeleteModal] = useState({ open: false, productId: null, productName: '' }); 

  const [categoryFilter, setCategoryFilter] = useState("ALL");

  const [countAllProduct , setCountAllProduct] = useState(0);
  const [countReadyProduct , setCounReadyProduct] = useState(0);
  const [, setCountMediumProduct] = useState(0); // นับไว้ แต่หน้านี้ยังไม่แสดง
  const [countLowProduct , setCountLowProduct] = useState(0);
  const [countOutofstockProduct , setCountOutofstockProduct] = useState(0);

 
  const [search, setSearch] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const PAGE_SIZE = 5;
  const LOW_STOCK_THRESHOLD = 5;
  // [Claude] 'all' | 'lowstock' (1-5) | 'outofstock' (0)
  const [stockFilter, setStockFilter] = useState('all');
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('');
  const [category] = useState("BREAD");
  const [expiryDate] = useState("");


  const [description, setDescription] = useState('');


  useEffect(() => {
    fetchProducts();
  }, []);
  
  // ดึงข้อมูลสินค้าเมื่อหน้าโหลด และเมื่อมีการเปลี่ยนแปลง
  const fetchProducts = async () => {

    try {
      let countAProduct = 0;
      let countReadyProduct = 0;
      let countMediumProduct = 0;
      let countLowProduct = 0;
      let countOutofstockProduct = 0;


      const res = await getproduct();
      res.data.forEach(p => {
       
        countAProduct++;

        if(p.stock >= 10){
          countReadyProduct++;
        } else if(p.stock >= 5){
          countMediumProduct++;
        } else if(p.stock > 0){
          countLowProduct++;
        } else {
          countOutofstockProduct++;
        }
      });

      setCounReadyProduct(countReadyProduct);
      setCountMediumProduct(countMediumProduct);
      setCountLowProduct(countLowProduct);
      setCountOutofstockProduct(countOutofstockProduct);
      setCountAllProduct(countAProduct);
      setProducts(res.data);
  
    } catch (err) {
      console.error(err);
    }
  };


  // [Claude] filter ผสม: category + search + stockFilter (all / lowstock / outofstock)
  const filteredProducts = products.filter(p => {
    const matchCategory  = categoryFilter === "ALL" || p.category === categoryFilter;
    const matchSearch    = boyerMooreContains(p.name ?? '', search);
    const matchStock =
      stockFilter === 'lowstock'    ? p.stock > 0 && p.stock <= LOW_STOCK_THRESHOLD :
      stockFilter === 'outofstock'  ? p.stock === 0 :
      true;
    return matchCategory && matchSearch && matchStock;
  });

  const totalPages = Math.ceil(filteredProducts.length / PAGE_SIZE);
  const pagedProducts = filteredProducts.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  // reset กลับหน้า 1 เมื่อ filter หรือ search เปลี่ยน
  const handleCategoryChange = (cat) => { setCategoryFilter(cat); setCurrentPage(1); };
  const handleSearchChange   = (e)   => { setSearch(e.target.value); setCurrentPage(1); };
  const handleStockFilter    = (val) => { setStockFilter(v => v === val ? 'all' : val); setCurrentPage(1); };

  
  // (Logic Functions - เหมือนเดิม)
  const handleAddProduct = async (e) => {
    e.preventDefault();
    
  
    if (!name || !price || !stock) {
     
      toast.error("กรุณากรอก ชื่อ, ราคา, และจำนวน");
      
      return;
    }
  
    const formData = new FormData();
    formData.append("image", imageFile);
    formData.append("name", name);
    formData.append("price", price);
    formData.append("stock", stock);
    formData.append("description", description);
    formData.append("category", category); 
    formData.append("expiryDate", expiryDate); // ใส่ที่เลิกรับ order
  
   try {
    const res = await addproduct(formData);
  
    const newProduct = res.data;
  
    setProducts(prev => [newProduct, ...prev]);
    setShowPreviewOfAddProduct(false); 
    setName('');
    setPrice('');
    setStock('');
    setDescription('');
    setImageSrc(null);
   
    fileInputRef.current.value = ""; 
    toast.success("เพิ่มสินค้าสำเร็จ");
  
  } catch (err) {
    console.error(err);
    toast.error("เพิ่มสินค้าไม่สำเร็จ");
  }
  };
  

  // [Claude] เปิด modal confirm ก่อนลบ
  const handleDeleteProduct = (id, name) => {
    setDeleteModal({ open: true, productId: id, productName: name });
  };

  // [Claude] ยืนยันลบจริง — เรียก API แล้วปิด modal
  const confirmDelete = async () => {
    try {
      await api.delete(`/api/v1/admin/products/${deleteModal.productId}`);
      setProducts(prev => prev.filter(p => p.id !== deleteModal.productId));
      toast.success('ลบสินค้าสำเร็จ');
    } catch {
      toast.error('ลบสินค้าไม่สำเร็จ');
    } finally {
      setDeleteModal({ open: false, productId: null, productName: '' });
    }
  };


  
  const getCroppedImg = (imageSrc, crop) => {
    return new Promise((resolve) => {
      const image = new Image();
      image.src = imageSrc;
  
      image.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = crop.width;
        canvas.height = crop.height;
  
        const ctx = canvas.getContext("2d");
  
        ctx.drawImage(
          image,
          crop.x,
          crop.y,
          crop.width,
          crop.height,
          0,
          0,
          crop.width,
          crop.height
        );
  
        canvas.toBlob((blob) => {
          const file = new File([blob], "cropped.jpeg", {
            type: "image/jpeg",
          });
          resolve(file);
        }, "image/jpeg");
      };
    });
  };
  




  const stockBadge = (stock) =>
    stock === 0 ? ['b-red', 'หมดสต็อก'] :
    stock <= 5  ? ['b-amber', 'ใกล้หมด'] :
    stock <= 9  ? ['b-blue', 'เหลือปานกลาง'] :
                  ['b-green', 'พร้อมขาย'];

  const productPreviewCard = (p) => (
    <div className="pcard">
      <div className="ph">
        {p.imageUrl
          ? <img src={`${API_URL}/${p.imageUrl}`} alt={p.name} />
          : <span className="ph-empty">ไม่มีรูปสินค้า</span>}
      </div>
      <div className="pmeta">
        <div style={{ minWidth: 0 }}>
          <h3>{p.name}</h3>
          <p className="desc">{p.description}</p>
          <div className="price">฿{p.price.toLocaleString()}<span className="stock">คงเหลือ {p.stock}</span></div>
        </div>
      </div>
    </div>
  );

  // (JSX - ส่วนแสดงผล)
  return (
    <>
      <div className="page-head">
        <div><h1>สินค้า</h1><p>ทั้งหมด {countAllProduct} รายการ</p></div>
        <div className="actions">
          <button className="btn btn-primary" onClick={() => router.push("/admin/products/addproduct")}>
            <Icon name="plus" size="sm" />เพิ่มสินค้า
          </button>
        </div>
      </div>

      <div className="stats">
        <button className={`stat button${stockFilter === 'all' ? ' on' : ''}`} onClick={() => handleStockFilter('all')}>
          <div className="k">ทั้งหมด</div><div className="v">{countAllProduct}</div>
        </button>
        <div className="stat">
          <div className="k">พร้อมขาย <span className="badge b-green plain">●</span></div><div className="v">{countReadyProduct}</div>
        </div>
        <button className={`stat button${stockFilter === 'lowstock' ? ' on' : ''}`} onClick={() => handleStockFilter('lowstock')}>
          <div className="k">สต็อกใกล้หมด <span className="badge b-amber plain">●</span></div><div className="v">{countLowProduct}</div>
        </button>
        <button className={`stat button${stockFilter === 'outofstock' ? ' on' : ''}`} onClick={() => handleStockFilter('outofstock')}>
          <div className="k">หมดสต็อก <span className="badge b-red plain">●</span></div><div className="v">{countOutofstockProduct}</div>
        </button>
      </div>

      {stockFilter !== 'all' && (
        <div className="alert-error" role="status"
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 16,
            ...(stockFilter === 'lowstock' ? { background: 'var(--amber-soft)', color: 'var(--amber)' } : {}) }}>
          <span style={{ display: 'flex', gap: 8, alignItems: 'center', fontWeight: 600 }}>
            <Icon name="alert" size="sm" />
            {stockFilter === 'outofstock'
              ? `กำลังแสดงเฉพาะสินค้าหมดสต็อก (stock = 0)`
              : `กำลังแสดงเฉพาะสินค้าใกล้หมด (stock 1–${LOW_STOCK_THRESHOLD} ชิ้น)`}
          </span>
          <button className="btn btn-ghost sm" style={{ color: 'inherit' }} onClick={() => handleStockFilter(stockFilter)}>ล้างตัวกรอง</button>
        </div>
      )}

      <section className="card">
        <div className="toolbar">
          <div className="chips">
            {['ALL', 'BREAD', 'CAKE', 'COOKIE'].map(cat => (
              <button key={cat} className={`chip${categoryFilter === cat ? ' on' : ''}`} onClick={() => handleCategoryChange(cat)}>
                {cat === 'ALL' ? 'ทั้งหมด' : CATEGORY_LABEL[cat]}
              </button>
            ))}
          </div>
          <label className="search">
            <Icon name="search" size="sm" />
            <input placeholder="ค้นหาสินค้า" aria-label="ค้นหาสินค้า" value={search} onChange={handleSearchChange} />
          </label>
        </div>

        <div className="table-wrap">
          <table className="t">
            <thead>
              <tr>
                <th>สินค้า</th><th className="hide-m">หมวด</th><th className="right">ราคา</th>
                <th className="right hide-m">คงเหลือ</th><th className="hide-m">ปิดรับออเดอร์</th><th>สถานะ</th><th></th>
              </tr>
            </thead>
            <tbody>
              {pagedProducts.length === 0 ? (
                <tr><td colSpan={7} className="muted" style={{ textAlign: 'center', padding: 48 }}>ไม่มีข้อมูลสินค้า</td></tr>
              ) : pagedProducts.map((product) => {
                const [badgeClass, badgeLabel] = stockBadge(product.stock);
                return (
                  <tr key={product.id}>
                    <td>
                      <div className="prod-cell">
                        {product.imageUrl
                          ? <img className="thumb" src={`${API_URL}/${product.imageUrl}`} alt={product.name} />
                          : <span className="thumb" />}
                        <div style={{ minWidth: 0 }}>
                          <b style={{ fontWeight: 600 }}>{product.name}</b>
                          <span className="sub" style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{product.description || '—'}</span>
                        </div>
                      </div>
                    </td>
                    <td className="hide-m"><span className="badge plain">{CATEGORY_LABEL[product.category] ?? product.category ?? '—'}</span></td>
                    <td className="num right">฿{Number(product.price).toLocaleString()}</td>
                    <td className="num right hide-m">{product.stock}</td>
                    <td className="num hide-m">
                      {product.expiryDate ? new Date(product.expiryDate).toLocaleDateString('th-TH', { dateStyle: 'medium' }) : '—'}
                    </td>
                    <td><span className={`badge ${badgeClass}`}>{badgeLabel}</span></td>
                    <td>
                      <div className="row-actions">
                        <button className="icon-btn sm hide-m" aria-label={`ดูรายละเอียด ${product.name}`} title="ดูรายละเอียด"
                          onClick={() => { setSelectedProduct(product); setShowPreview(true); }}><Icon name="eye" size="sm" /></button>
                        <button className="icon-btn sm" aria-label={`แก้ไข ${product.name}`} title="แก้ไข"
                          onClick={() => router.push(`/admin/products/editproduct/${product.id}`)}><Icon name="edit" size="sm" /></button>
                        <button className="icon-btn sm hide-m" aria-label={`ลบ ${product.name}`} title="ลบ" style={{ color: 'var(--red)' }}
                          onClick={() => handleDeleteProduct(product.id, product.name)}><Icon name="trash" size="sm" /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="pager">
          <span>
            {filteredProducts.length === 0
              ? 'ไม่มีสินค้า'
              : `แสดง ${(currentPage - 1) * PAGE_SIZE + 1}–${Math.min(currentPage * PAGE_SIZE, filteredProducts.length)} จาก ${filteredProducts.length} สินค้า`}
          </span>
          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <button className="btn btn-outline sm" onClick={() => setCurrentPage(p => Math.max(p - 1, 1))} disabled={currentPage === 1}>ก่อนหน้า</button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map(page => (
                <button key={page} className={`btn sm ${currentPage === page ? 'btn-dark' : 'btn-outline'}`} onClick={() => setCurrentPage(page)}
                  aria-current={currentPage === page ? 'page' : undefined}>{page}</button>
              ))}
              <button className="btn btn-outline sm" onClick={() => setCurrentPage(p => Math.min(p + 1, totalPages))} disabled={currentPage === totalPages}>ถัดไป</button>
            </div>
          )}
        </div>
      </section>

      {/* (Modal แสดง Preview ก่อนเพิ่มสินค้า) */}
      {showPreviewOfAddProduct && (
        <div className="modal-scrim" onClick={() => setShowPreviewOfAddProduct(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head"><h2>Preview สินค้า</h2></div>
            <div className="modal-body rows">
              <div className="r"><span className="muted">ชื่อ</span><span>{name}</span></div>
              <div className="r"><span className="muted">ราคา</span><span>{price}</span></div>
              <div className="r"><span className="muted">จำนวน</span><span>{stock}</span></div>
              <div className="r"><span className="muted">รายละเอียด</span><span>{description}</span></div>
              <div className="r"><span className="muted">วันปิดรับ order</span><span>{expiryDate}</span></div>
              {imageFile && <img src={URL.createObjectURL(imageFile)} alt="preview" style={{ marginTop: 8, height: 160, width: '100%', objectFit: 'cover', borderRadius: 'var(--r-md)' }} />}
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" onClick={() => setShowPreviewOfAddProduct(false)}>ยกเลิก</button>
              <button className="btn btn-primary" onClick={handleAddProduct}>ยืนยัน</button>
            </div>
          </div>
        </div>
      )}

      {/* (Modal แสดง การแก้ไขข้อมูลสินค้า */}
      {showEditProduct && selectedProduct && (
        <div className="modal-scrim" onClick={() => setShowPreview(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-body">{productPreviewCard(selectedProduct)}</div>
            <div className="modal-foot"><button className="btn btn-outline" onClick={() => setshowEditProduct(false)}>ปิด</button></div>
          </div>
        </div>
      )}

      {/* (Modal แสดง Preview ข้อมูลสินค้าในตาราง) */}
      {showPreview && selectedProduct && (
        <div className="modal-scrim" onClick={() => setShowPreview(false)}>
          <div className="modal" role="dialog" aria-label={selectedProduct.name} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h2>รายละเอียดสินค้า</h2>
              <button className="icon-btn" aria-label="ปิด" onClick={() => setShowPreview(false)}><Icon name="x" /></button>
            </div>
            <div className="modal-body">{productPreviewCard(selectedProduct)}</div>
            <div className="modal-foot"><button className="btn btn-outline" onClick={() => setShowPreview(false)}>ปิด</button></div>
          </div>
        </div>
      )}

      {/* [Claude] Modal ยืนยันการลบสินค้า */}
      {deleteModal.open && (
        <div className="modal-scrim" onClick={() => setDeleteModal({ open: false, productId: null, productName: '' })}>
          <div className="modal" role="alertdialog" aria-label="ลบสินค้า" style={{ maxWidth: 360 }} onClick={e => e.stopPropagation()}>
            <div className="modal-body" style={{ textAlign: 'center', paddingTop: 28 }}>
              <div className="empty" style={{ border: 0, padding: 0, background: 'none' }}>
                <div className="ic" style={{ background: 'var(--red-soft)', color: 'var(--red)' }}><Icon name="trash" /></div>
              </div>
              <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>ลบสินค้า</h2>
              <p className="muted" style={{ margin: '6px 0 0', fontSize: 14 }}>
                คุณต้องการลบ <b style={{ color: 'var(--ink)' }}>{deleteModal.productName}</b> ใช่หรือไม่?
              </p>
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setDeleteModal({ open: false, productId: null, productName: '' })}>ยกเลิก</button>
              <button className="btn btn-danger" style={{ flex: 1 }} onClick={confirmDelete}>ลบ</button>
            </div>
          </div>
        </div>
      )}

      {/* (Modal สำหรับ Crop รูปภาพ) */}
      {showCrop && (
        <div className="modal-scrim">
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head"><h2>ปรับรูปภาพ</h2></div>
            <div className="modal-body">
              <div style={{ position: 'relative', width: '100%', height: 256, background: 'var(--line)', borderRadius: 'var(--r-md)', overflow: 'hidden' }}>
                <Cropper
                  image={imageSrc}
                  crop={crop}
                  zoom={zoom}
                  aspect={1}
                  onCropChange={setCrop}
                  onZoomChange={setZoom}
                  onCropComplete={(croppedArea, croppedPixels) => setCroppedAreaPixels(croppedPixels)}
                />
              </div>
              <input type="range" min={1} max={3} step={0.1} value={zoom} aria-label="ซูม"
                onChange={(e) => setZoom(e.target.value)} style={{ width: '100%', marginTop: 16, accentColor: 'var(--blue)' }} />
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" onClick={() => { setShowCrop(false); setImageSrc(null); }}>ยกเลิก</button>
              <button className="btn btn-primary" onClick={async () => {
                const cropped = await getCroppedImg(imageSrc, croppedAreaPixels);
                setImageFile(cropped);
                setShowCrop(false);
                setImageSrc(null);
              }}>ตกลง</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
