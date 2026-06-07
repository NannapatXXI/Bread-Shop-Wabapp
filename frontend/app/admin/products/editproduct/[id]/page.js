'use client';

import { useState, useRef, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import toast from 'react-hot-toast';
import Cropper from 'react-easy-crop';
import { getProductById, updateProduct } from '@/services/auth.service';
import { FiUploadCloud, FiX, FiCheck } from 'react-icons/fi';

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

  const inputClass = "mt-1 block w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-[#0F2235]/20 focus:border-[#0F2235] transition";
  const labelClass = "block text-sm font-medium text-gray-600 mb-1";

  // รูปที่จะแสดง preview
  const displayImage = imagePreviewUrl || (currentImageUrl ? `${API_URL}/${currentImageUrl}` : null);

  if (loading) return (
    <div className="min-h-screen bg-[#EEF4FB] flex items-center justify-center">
      <p className="text-gray-400">กำลังโหลด...</p>
    </div>
  );

  return (
    <div className="min-h-screen bg-[#EEF4FB] p-6 md:p-8">

      {/* Header */}
      <div className="mb-8 flex items-center gap-4">
        <button
          onClick={() => router.push('/admin/products')}
          className="flex items-center justify-center w-9 h-9 rounded-xl bg-white border border-gray-200 text-gray-500 hover:bg-[#0F2235] hover:text-white hover:border-[#0F2235] transition shadow-sm"
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div>
          <h1 className="text-2xl font-bold text-[#0F2235]">แก้ไขสินค้า</h1>
          <p className="text-gray-400 text-sm mt-1">แก้ไขข้อมูลสินค้า ID: {id}</p>
        </div>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">

        {/* Card Header */}
        <div className="bg-[#0F2235] px-6 py-4">
          <h2 className="text-white font-semibold text-base">ข้อมูลสินค้า</h2>
        </div>

        <div className="p-6 md:p-8">
          <form onSubmit={handleSave}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

              {/* ชื่อสินค้า */}
              <div>
                <label className={labelClass}>ชื่อสินค้า <span className="text-red-400">*</span></label>
                <input type="text" value={name} onChange={e => setName(e.target.value)}
                  placeholder="เช่น ครัวซองค์เนย"
                  className={inputClass} />
              </div>

              {/* ราคา */}
              <div>
                <label className={labelClass}>ราคา (บาท) <span className="text-red-400">*</span></label>
                <input type="number" min="0" value={price}
                  onChange={(e) => { const v = Number(e.target.value); if (v >= 0) setPrice(v); }}
                  placeholder="0" className={inputClass} />
              </div>

              {/* จำนวน */}
              <div>
                <label className={labelClass}>จำนวนคงเหลือ <span className="text-red-400">*</span></label>
                <input type="number" min="0" value={stock}
                  onChange={(e) => { const v = Number(e.target.value); if (v >= 0) setStock(v); }}
                  placeholder="0" className={inputClass} />
              </div>

              {/* หมวดหมู่ */}
              <div>
                <label className={labelClass}>หมวดหมู่สินค้า <span className="text-red-400">*</span></label>
                <select value={category} onChange={(e) => setCategory(e.target.value)} className={inputClass}>
                  <option value="BREAD">🍞 Bread</option>
                  <option value="CAKE">🎂 Cake</option>
                  <option value="DRINK">🥤 Drink</option>
                  <option value="COOKIE">🍪 Cookie</option>
                </select>
              </div>

              {/* วันปิดรับ order */}
              <div>
                <label className={labelClass}>วันปิดรับ order</label>
                <input type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)}
                  className={inputClass} />
              </div>

              {/* รูปภาพ */}
              <div>
                <label className={labelClass}>รูปภาพสินค้า</label>
                <div
                  onClick={() => fileInputRef.current?.click()}
                  className="mt-1 border-2 border-dashed border-gray-200 rounded-xl p-4 flex items-center gap-3 cursor-pointer hover:border-[#0F2235] hover:bg-gray-50 transition"
                >
                  {displayImage ? (
                    <>
                      <img src={displayImage} alt="preview" className="h-12 w-12 object-cover rounded-lg flex-shrink-0" />
                      <div>
                        <span className="text-sm text-gray-500 block">เปลี่ยนรูปภาพ</span>
                        {imageFile && <span className="text-xs text-[#0F2235] font-medium">มีรูปใหม่พร้อมบันทึก</span>}
                      </div>
                    </>
                  ) : (
                    <>
                      <FiUploadCloud className="text-gray-400 text-2xl flex-shrink-0" />
                      <span className="text-sm text-gray-400">คลิกเพื่ออัปโหลดรูป</span>
                    </>
                  )}
                </div>
                <input type="file" ref={fileInputRef} onChange={handleImageChange} className="hidden" accept="image/*" />
              </div>

              {/* รายละเอียด */}
              <div className="md:col-span-2">
                <label className={labelClass}>รายละเอียด</label>
                <textarea value={description} onChange={e => setDescription(e.target.value)} rows="3"
                  placeholder="รายละเอียดสินค้า..."
                  className={inputClass + " resize-none"} />
              </div>

            </div>

            {/* Buttons */}
            <div className="flex justify-end gap-3 mt-8 pt-6 border-t border-gray-100">
              <button type="button" onClick={() => router.push('/admin/products')}
                className="px-6 py-2.5 text-sm font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition">
                ยกเลิก
              </button>
              <button type="button" onClick={() => setShowPreview(true)}
                disabled={!name || !price || !stock}
                className="px-6 py-2.5 text-sm font-medium text-white bg-[#0F2235] hover:bg-[#1a3a5c] rounded-xl transition disabled:opacity-40 disabled:cursor-not-allowed">
                บันทึก
              </button>
            </div>

          </form>
        </div>
      </div>

      {/* Modal Preview ยืนยัน */}
      {showPreview && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setShowPreview(false)}>
          <div className="bg-white rounded-2xl w-96 shadow-2xl overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="bg-[#0F2235] px-6 py-4 flex justify-between items-center">
              <h2 className="text-white font-semibold">ยืนยันการแก้ไขสินค้า</h2>
              <button onClick={() => setShowPreview(false)} className="text-gray-400 hover:text-white">
                <FiX size={18} />
              </button>
            </div>
            <div className="p-6">
              {displayImage && (
                <img src={displayImage} alt="preview"
                  className="w-full h-40 object-cover rounded-xl mb-4" />
              )}
              <div className="space-y-2 text-sm text-gray-700">
                <div className="flex justify-between"><span className="text-gray-400">ชื่อสินค้า</span><span className="font-medium">{name}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">ราคา</span><span className="font-medium">฿{price}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">จำนวน</span><span className="font-medium">{stock}</span></div>
                <div className="flex justify-between"><span className="text-gray-400">หมวดหมู่</span><span className="font-medium">{category}</span></div>
                {expiryDate && <div className="flex justify-between"><span className="text-gray-400">วันปิดรับ</span><span className="font-medium">{expiryDate}</span></div>}
              </div>
              <div className="flex gap-3 mt-6">
                <button onClick={() => setShowPreview(false)}
                  className="flex-1 py-2.5 text-sm font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition">
                  แก้ไข
                </button>
                <button onClick={() => { setShowPreview(false); handleSave(); }}
                  className="flex-1 py-2.5 text-sm font-medium text-white bg-[#0F2235] hover:bg-[#1a3a5c] rounded-xl transition flex items-center justify-center gap-2">
                  <FiCheck size={16} /> ยืนยัน
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal Crop */}
      {showCrop && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl w-[420px] p-6 shadow-2xl" onClick={e => e.stopPropagation()}>
            <h2 className="text-base font-semibold text-[#0F2235] mb-4">ปรับรูปภาพ</h2>
            <div className="relative w-full h-64 bg-gray-100 rounded-xl overflow-hidden">
              <Cropper image={imageSrc} crop={crop} zoom={zoom} aspect={1}
                onCropChange={setCrop} onZoomChange={setZoom}
                onCropComplete={(_, pixels) => setCroppedAreaPixels(pixels)} />
            </div>
            <input type="range" min={1} max={3} step={0.1} value={zoom}
              onChange={(e) => setZoom(e.target.value)} className="w-full mt-4 accent-[#0F2235]" />
            <div className="flex gap-3 mt-4">
              <button onClick={() => { setShowCrop(false); setImageSrc(null); if (fileInputRef.current) fileInputRef.current.value = ''; }}
                className="flex-1 py-2.5 text-sm font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition">
                ยกเลิก
              </button>
              <button onClick={async () => {
                const cropped = await getCroppedImg(imageSrc, croppedAreaPixels);
                setImageFile(cropped);
                setImagePreviewUrl(URL.createObjectURL(cropped));
                setShowCrop(false);
                setImageSrc(null);
              }} className="flex-1 py-2.5 text-sm font-medium text-white bg-[#0F2235] hover:bg-[#1a3a5c] rounded-xl transition">
                ตกลง
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
