'use client';

import { useState, useRef, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import Cropper from 'react-easy-crop';
import { getProductById, updateProduct } from '@/services/auth.service';
import Icon from '../../../../components/Icon';
import { CATEGORY_LABEL } from '../../../../components/ProductCard';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function EditProductPage() {
  const { id } = useParams();
  const router = useRouter();

  const [name, setName]               = useState('');
  const [price, setPrice]             = useState('');
  const [stock, setStock]             = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory]       = useState('BREAD');
  const [expiryDate, setExpiryDate]   = useState('');
  const [currentImageUrl, setCurrentImageUrl] = useState(null);

  const fileInputRef = useRef(null);
  const [imageSrc, setImageSrc]               = useState(null);
  const [imageFile, setImageFile]             = useState(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState(null);
  const [showCrop, setShowCrop]               = useState(false);
  const [crop, setCrop]                       = useState({ x: 0, y: 0 });
  const [zoom, setZoom]                       = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null);
  const [showPreview, setShowPreview]         = useState(false);
  const [loading, setLoading]                 = useState(true);

  useEffect(() => {
    if (!id) return;
    getProductById(id)
      .then((res) => {
        const p = res.data;
        setName(p.name || '');
        setPrice(p.price || '');
        setStock(p.stock || '');
        setDescription(p.description || '');
        setCategory(p.category || 'BREAD');
        setExpiryDate(p.expiryDate || '');
        setCurrentImageUrl(p.imageUrl || null);
      })
      .catch(() => toast.error('โหลดข้อมูลสินค้าไม่สำเร็จ'))
      .finally(() => setLoading(false));
  }, [id]);

  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { setImageSrc(reader.result); setShowCrop(true); };
    reader.readAsDataURL(file);
  };

  const getCroppedImg = (src, cropPx) =>
    new Promise((resolve) => {
      const image = new Image();
      image.src = src;
      image.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = cropPx.width;
        canvas.height = cropPx.height;
        canvas.getContext('2d').drawImage(image, cropPx.x, cropPx.y, cropPx.width, cropPx.height, 0, 0, cropPx.width, cropPx.height);
        canvas.toBlob((blob) => resolve(new File([blob], 'cropped.jpeg', { type: 'image/jpeg' })), 'image/jpeg');
      };
    });

  const handleSave = async (e) => {
    e?.preventDefault();
    if (!name || !price || !stock) {
      toast.error('กรุณากรอก ชื่อ, ราคา, และจำนวน');
      return;
    }
    const formData = new FormData();
    formData.append('name', name);
    formData.append('price', price);
    formData.append('stock', stock);
    formData.append('description', description);
    formData.append('category', category);
    formData.append('expiryDate', expiryDate);
    if (imageFile) formData.append('image', imageFile);

    try {
      await updateProduct(id, formData);
      toast.success('แก้ไขสินค้าสำเร็จ');
      router.push('/admin/products');
    } catch {
      toast.error('แก้ไขสินค้าไม่สำเร็จ');
    }
  };

  // รูปที่จะแสดง preview
  const displayImage = imagePreviewUrl || (currentImageUrl ? `${API_URL}/${currentImageUrl}` : null);

  const header = (
    <div className="page-head">
      <div className="back">
        <button className="icon-btn" aria-label="กลับ" onClick={() => router.push('/admin/products')} style={{ border: '1px solid var(--line)', background: 'var(--surface)' }}>
          <Icon name="left" />
        </button>
        <div><h1>แก้ไขสินค้า</h1><p>{loading ? 'กำลังโหลด...' : `${name} · ID ${id}`}</p></div>
      </div>
    </div>
  );

  if (loading) return (
    <>
      {header}
      <div className="card pad" style={{ maxWidth: 1080, display: 'flex', flexDirection: 'column', gap: 16 }}>
        {Array.from({ length: 4 }).map((_, i) => <div key={i} className="sk" style={{ height: 44 }} />)}
        <div className="sk" style={{ height: 120 }} />
      </div>
    </>
  );

  return (
    <>
      {header}

      <div className="two" style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 24, alignItems: 'start', maxWidth: 1080 }}>
        <section className="card">
          <div className="card-head"><h2>ข้อมูลสินค้า</h2></div>
          <form className="pad grid-2" onSubmit={handleSave}>
            <div className="field">
              <label htmlFor="pd-name">ชื่อสินค้า <span className="req">*</span></label>
              <div className="control"><input id="pd-name" type="text" value={name} onChange={e => setName(e.target.value)} placeholder="เช่น ครัวซองต์เนย" /></div>
            </div>
            <div className="field">
              <label htmlFor="pd-cat">หมวดหมู่ <span className="req">*</span></label>
              <div className="control">
                <select id="pd-cat" value={category} onChange={(e) => setCategory(e.target.value)}>
                  <option value="BREAD">ขนมปัง</option>
                  <option value="CAKE">เค้ก</option>
                  <option value="DRINK">เครื่องดื่ม</option>
                  <option value="COOKIE">คุกกี้</option>
                </select>
              </div>
            </div>
            <div className="field">
              <label htmlFor="pd-price">ราคา <span className="req">*</span></label>
              <div className="control"><span>฿</span>
                <input id="pd-price" type="number" min="0" inputMode="decimal" value={price} placeholder="0"
                  onChange={(e) => { const v = Number(e.target.value); if (v >= 0) setPrice(v); }} />
              </div>
            </div>
            <div className="field">
              <label htmlFor="pd-stock">จำนวนคงเหลือ <span className="req">*</span></label>
              <div className="control">
                <input id="pd-stock" type="number" min="0" inputMode="numeric" value={stock} placeholder="0"
                  onChange={(e) => { const v = Number(e.target.value); if (v >= 0) setStock(v); }} />
                <span style={{ whiteSpace: 'nowrap' }}>ชิ้น</span>
              </div>
            </div>
            <div className="field">
              <label htmlFor="pd-exp">วันปิดรับออเดอร์</label>
              <div className="control"><input id="pd-exp" type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} /></div>
              <span className="hint">เว้นว่างไว้ถ้าขายตลอด</span>
            </div>
            <div />
            <div className="field span-2">
              <label>รูปสินค้า</label>
              <div className="upload" role="button" tabIndex={0} style={{ cursor: 'pointer', ...(displayImage ? {} : { justifyContent: 'center', flexDirection: 'column', padding: 28, textAlign: 'center' }) }}
                onClick={() => fileInputRef.current?.click()}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), fileInputRef.current?.click())}>
                {displayImage ? (
                  <>
                    <img src={displayImage} alt="preview" />
                    <div>
                      <b style={{ color: 'var(--ink)', fontWeight: 600 }}>เปลี่ยนรูปภาพ</b>
                      <div className="hint">{imageFile ? <span style={{ color: "var(--blue)", fontWeight: 500 }}>มีรูปใหม่พร้อมบันทึก</span> : "คลิกเพื่อเปลี่ยนรูป"}</div>
                    </div>
                  </>
                ) : (
                  <>
                    <span className="ic" style={{ width: 40, height: 40, borderRadius: '50%', background: 'var(--surface)', display: 'grid', placeItems: 'center' }}><Icon name="upload" /></span>
                    <div><b style={{ color: 'var(--ink)', fontWeight: 600 }}>คลิกเพื่ออัปโหลดรูป</b><div className="hint">แนะนำภาพสี่เหลี่ยมจัตุรัส · ปรับครอปได้หลังเลือกรูป</div></div>
                  </>
                )}
              </div>
              <input type="file" ref={fileInputRef} onChange={handleImageChange} hidden accept="image/*" />
            </div>
            <div className="field span-2">
              <label htmlFor="pd-desc">รายละเอียด</label>
              <textarea id="pd-desc" className="control" value={description} onChange={e => setDescription(e.target.value)} rows={3} placeholder="รสชาติ ส่วนผสม หรือข้อควรรู้" />
            </div>
          </form>
        </section>
        <aside style={{ position: 'sticky', top: 88 }}>
          <section className="card pad" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button className="btn btn-primary btn-block" onClick={() => setShowPreview(true)} disabled={!name || !price || !stock}>บันทึก</button>
            <button className="btn btn-outline btn-block" onClick={() => router.push("/admin/products")}>ยกเลิก</button>
          </section>
        </aside>
      </div>

      {showPreview && (
        <div className="modal-scrim" onClick={() => setShowPreview(false)}>
          <div className="modal" role="dialog" aria-label="ยืนยันการแก้ไขสินค้า" onClick={e => e.stopPropagation()}>
            <div className="modal-head">
              <h2>ยืนยันการแก้ไขสินค้า</h2>
              <button className="icon-btn" aria-label="ปิด" onClick={() => setShowPreview(false)}><Icon name="x" /></button>
            </div>
            <div className="modal-body">
              {displayImage && <img src={displayImage} alt="preview" style={{ width: "100%", height: 160, objectFit: "cover", borderRadius: "var(--r-md)", marginBottom: 16 }} />}
              <div className="rows">
                <div className="r"><span className="muted">ชื่อสินค้า</span><b style={{ fontWeight: 600 }}>{name}</b></div>
                <div className="r"><span className="muted">ราคา</span><span className="num">฿{price}</span></div>
                <div className="r"><span className="muted">จำนวน</span><span className="num">{stock}</span></div>
                <div className="r"><span className="muted">หมวดหมู่</span><span>{CATEGORY_LABEL[category] ?? category}</span></div>
                {expiryDate && <div className="r"><span className="muted">วันปิดรับ</span><span>{expiryDate}</span></div>}
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowPreview(false)}>แก้ไข</button>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={() => { setShowPreview(false); handleSave(); }}><Icon name="check" size="sm" />ยืนยัน</button>
            </div>
          </div>
        </div>
      )}

      {showCrop && (
        <div className="modal-scrim">
          <div className="modal" role="dialog" aria-label="ปรับรูปภาพ" onClick={e => e.stopPropagation()}>
            <div className="modal-head"><h2>ปรับรูปภาพ</h2></div>
            <div className="modal-body">
              <div style={{ position: 'relative', width: '100%', height: 256, background: 'var(--line)', borderRadius: 'var(--r-md)', overflow: 'hidden' }}>
                <Cropper image={imageSrc} crop={crop} zoom={zoom} aspect={1}
                  onCropChange={setCrop} onZoomChange={setZoom}
                  onCropComplete={(_, pixels) => setCroppedAreaPixels(pixels)} />
              </div>
              <input type="range" min={1} max={3} step={0.1} value={zoom} aria-label="ซูม"
                onChange={(e) => setZoom(e.target.value)} style={{ width: '100%', marginTop: 16, accentColor: 'var(--blue)' }} />
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => { setShowCrop(false); setImageSrc(null); if (fileInputRef.current) fileInputRef.current.value = ''; }}>ยกเลิก</button>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={async () => {
                const cropped = await getCroppedImg(imageSrc, croppedAreaPixels);
                setImageFile(cropped);
                setImagePreviewUrl(URL.createObjectURL(cropped));
                setShowCrop(false);
                setImageSrc(null);
              }}>ตกลง</button>
            </div>
          </div>
        </div>
      )}
      <style>{`@media(max-width:1100px){.two{grid-template-columns:1fr!important}.two aside{position:static!important}}`}</style>
    </>
  );
}
