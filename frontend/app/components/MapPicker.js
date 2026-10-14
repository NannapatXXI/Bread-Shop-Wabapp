'use client';

// [Claude] MapPicker — แผนที่ให้ user ปักหมุดแล้วดึงที่อยู่จาก Nominatim (OpenStreetMap)
// ไฟล์นี้ต้อง dynamic import { ssr: false } เพราะ Leaflet ใช้ window

import { useState } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

// [Claude] แก้ปัญหา default marker icon หายใน Next.js (webpack ไม่ bundle assets ของ leaflet)
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

// ─── ClickHandler ──────────────────────────────────────────
// Component ภายใน MapContainer สำหรับรับ event คลิก
function ClickHandler({ onMapClick }) {
  useMapEvents({
    click(e) {
      onMapClick(e.latlng);
    },
  });
  return null;
}

// ─── MapPicker ─────────────────────────────────────────────
// Props:
//   onConfirm(addressData)  — callback เมื่อกดตกลง พร้อมข้อมูลที่อยู่
//   onClose()               — callback เมื่อปิด modal
export default function MapPicker({ onConfirm, onClose }) {
  // ค่า default อยู่กลางประเทศไทยพอดี (กรุงเทพ)
  const DEFAULT_CENTER = [13.7563, 100.5018];
  const DEFAULT_ZOOM   = 13;

  const [markerPos, setMarkerPos]   = useState(null);     // ตำแหน่งที่ปักหมุด
  const [loading, setLoading]       = useState(false);    // กำลัง reverse geocode
  const [geoResult, setGeoResult]   = useState(null);     // ผลลัพธ์จาก Nominatim

  // [Claude] เมื่อ user คลิกบนแผนที่ → ปักหมุด + เรียก Nominatim
  const handleMapClick = async (latlng) => {
    setMarkerPos(latlng);
    setLoading(true);
    setGeoResult(null);

    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${latlng.lat}&lon=${latlng.lng}&format=json&accept-language=th`,
        { headers: { 'Accept-Language': 'th' } }
      );
      const data = await res.json();

      const a = data.address || {};

      // [Claude] แมป field ของ Nominatim → field ของฟอร์มที่อยู่
      // Nominatim ไทยใช้หลาย field name ต่างกันตามพื้นที่ ต้องลองหลาย key
      const mapped = {
        // ที่อยู่: บ้านเลขที่ + ถนน/ซอย
        address: [a.house_number, a.road, a.pedestrian, a.footway, a.path]
                   .filter(Boolean).join(' '),

        // ตำบล/แขวง: ไทยใช้ suburb (กทม.) หรือ subdistrict (ต่างจังหวัด)
        subdistrict: a.suburb || a.subdistrict || a.village
                     || a.hamlet || a.neighbourhood || a.quarter || '',

        // อำเภอ/เขต: ไทยใช้ city_district (กทม.) หรือ state_district/district
        district: a.city_district || a.state_district || a.district || a.county || '',

        // จังหวัด: ไทยใช้ state หรือ province (กทม. บางครั้งอยู่ใน city)
        province: a.state || a.province || a.city || '',

        postcode: a.postcode || '',
      };


      setGeoResult(mapped);
    } catch {
    } finally {
      setLoading(false);
    }
  };

  // [Claude] กดตกลง → ส่งข้อมูลกลับไปที่ parent
  const handleConfirm = () => {
    if (!geoResult) return;
    onConfirm(geoResult);
  };

  return (
    // overlay
    <div className="fixed inset-0 bg-black bg-opacity-60 flex items-center justify-center z-[9999]"
         onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[680px] max-w-[95vw] overflow-hidden"
           onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div>
            <h3 className="font-semibold text-gray-800">เลือกตำแหน่งจากแผนที่</h3>
            <p className="text-xs text-gray-400 mt-0.5">คลิกบนแผนที่เพื่อปักหมุดที่อยู่</p>
          </div>
          <button onClick={onClose}
                  className="text-gray-400 hover:text-gray-600 text-xl leading-none">✕</button>
        </div>

        {/* Map */}
        <div className="relative" style={{ height: '380px' }}>
          <MapContainer
            center={DEFAULT_CENTER}
            zoom={DEFAULT_ZOOM}
            style={{ height: '100%', width: '100%' }}
            scrollWheelZoom={true}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <ClickHandler onMapClick={handleMapClick} />
            {markerPos && <Marker position={markerPos} />}
          </MapContainer>

          {/* Tip overlay */}
          {!markerPos && (
            <div className="absolute top-3 left-1/2 -translate-x-1/2 bg-white bg-opacity-90 text-xs text-gray-600 px-3 py-1.5 rounded-full shadow pointer-events-none z-[1000]">
              📍 แตะบนแผนที่เพื่อเลือกตำแหน่ง
            </div>
          )}
        </div>

        {/* Address Preview */}
        <div className="px-5 py-3 bg-gray-50 border-t border-gray-100 min-h-[80px]">
          {loading && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <svg className="animate-spin w-4 h-4 text-indigo-400" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
              </svg>
              กำลังค้นหาที่อยู่...
            </div>
          )}

          {/* [Claude] แสดงข้อมูลแยก field ให้ user เห็นว่าจะกรอกอะไรลงฟอร์ม */}
          {!loading && geoResult && (
            <div>
              <p className="text-xs font-semibold text-indigo-600 mb-2">📍 ข้อมูลที่จะกรอกลงฟอร์ม:</p>
              <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                {[
                  ['ที่อยู่', geoResult.address],
                  ['ตำบล / แขวง', geoResult.subdistrict],
                  ['อำเภอ / เขต', geoResult.district],
                  ['จังหวัด', geoResult.province],
                  ['รหัสไปรษณีย์', geoResult.postcode],
                ].map(([k, v]) => (
                  <div key={k} className="flex items-baseline gap-1">
                    <span className="text-xs text-gray-400 shrink-0">{k}:</span>
                    <span className="text-xs font-medium text-gray-700">{v || <span className="text-gray-300 italic">ไม่พบ</span>}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {!loading && !geoResult && !markerPos && (
            <p className="text-xs text-gray-400 italic">คลิกบนแผนที่เพื่อเริ่มต้น</p>
          )}
        </div>

        {/* Footer Buttons */}
        <div className="flex justify-end gap-3 px-5 py-4 border-t border-gray-100">
          <button onClick={onClose}
                  className="px-5 py-2 text-sm rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 transition">
            ยกเลิก
          </button>
          <button onClick={handleConfirm}
                  disabled={!geoResult || loading}
                  className={`px-5 py-2 text-sm rounded-lg text-white transition font-medium
                    ${geoResult && !loading
                      ? 'bg-indigo-500 hover:bg-indigo-600'
                      : 'bg-gray-300 cursor-not-allowed'}`}>
            ใช้ที่อยู่นี้
          </button>
        </div>
      </div>
    </div>
  );
}
