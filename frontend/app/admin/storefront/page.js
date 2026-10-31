'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import Icon from '@/app/components/Icon';
import {
  adminGetStorefront, adminSetFeatured, adminUploadHeroImage, adminRemoveHeroImage, getAdminProducts,
} from '@/services/auth.service';

const API_URL = process.env.NEXT_PUBLIC_API_URL;
const MAX_FEATURED = 8;   // ตรงกับ FeaturedRequest ฝั่ง backend
const SHOWN = 4;          // ลูกค้าเห็น 4 อันดับแรกที่ยังมีของ

/**
 * หน้าร้าน — admin ตั้งรูปปกหน้า home และเลือก/เรียงสินค้าแนะนำ
 * (หน้าแรก "เมนูขายดี" และหน้า home "สินค้าแนะนำ" ใช้ชุดเดียวกัน)
 */
export default function AdminStorefrontPage() {
  const [products, setProducts] = useState([]);
  const [heroImageUrl, setHeroImageUrl] = useState(null);
  const [picked, setPicked] = useState([]);      // id ตามลำดับที่จะแสดง
  const [saved, setSaved] = useState([]);        // ที่บันทึกล่าสุด — ไว้รู้ว่ามีอะไรยังไม่บันทึก
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [search, setSearch] = useState('');
  const fileRef = useRef(null);

  useEffect(() => {
    Promise.all([adminGetStorefront(), getAdminProducts()])
      .then(([sf, ps]) => {
        setHeroImageUrl(sf.data?.heroImageUrl ?? null);
        setPicked(sf.data?.featuredProductIds ?? []);
        setSaved(sf.data?.featuredProductIds ?? []);
        setProducts(ps.data ?? []);
      })
      .catch(() => toast.error('โหลดข้อมูลหน้าร้านไม่สำเร็จ'))
      .finally(() => setLoading(false));
  }, []);

  const byId = useMemo(() => Object.fromEntries(products.map(p => [p.id, p])), [products]);
  const dirty = picked.join(',') !== saved.join(',');
  const sellable = (p) => p && p.isActive !== false && p.stock > 0;
  const visibleToCustomers = picked.filter(id => sellable(byId[id])).slice(0, SHOWN);
  const matches = products.filter(p => p.name.toLowerCase().includes(search.trim().toLowerCase()));

  // ─── รูปปก ───────────────────────────────────────────────
  const handleUpload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';                 // เลือกไฟล์เดิมซ้ำได้
    if (!file) return;
    setUploading(true);
    try {
      const res = await adminUploadHeroImage(file);
      setHeroImageUrl(res.data.heroImageUrl);
      toast.success('เปลี่ยนรูปปกสำเร็จ');
    } catch (err) {
      toast.error(err?.message ?? 'อัปโหลดไม่สำเร็จ');
    } finally { setUploading(false); }
  };

  const handleRemoveHero = async () => {
    try {
      await adminRemoveHeroImage();
      setHeroImageUrl(null);
      toast.success('กลับไปใช้รูปปกตั้งต้นแล้ว');
    } catch { toast.error('ไม่สำเร็จ กรุณาลองใหม่'); }
  };

  // ─── สินค้าแนะนำ ─────────────────────────────────────────
  const add = (id) => setPicked(prev => (prev.length >= MAX_FEATURED ? prev : [...prev, id]));
  const remove = (id) => setPicked(prev => prev.filter(x => x !== id));
  const move = (index, delta) => setPicked(prev => {
    const next = [...prev];
    const target = index + delta;
    if (target < 0 || target >= next.length) return prev;
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });

  const handleSave = async () => {
    setSaving(true);
    try {
      await adminSetFeatured(picked);
      setSaved(picked);
      toast.success('บันทึกสินค้าแนะนำสำเร็จ');
    } catch (err) {
      toast.error(err?.message ?? 'บันทึกไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  if (loading) {
    return <div className="sk" style={{ height: 320, borderRadius: 'var(--r-lg)' }} />;
  }

  return (
    <>
      <div className="page-head">
        <div><h1>หน้าร้าน</h1><p>รูปปกหน้า home และสินค้าแนะนำที่ลูกค้าเห็นเป็นอย่างแรก</p></div>
      </div>

      {/* ── รูปปก ── */}
      <section className="card pad" aria-labelledby="hero-h" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ width: 240, maxWidth: '100%', aspectRatio: '4/3', borderRadius: 'var(--r-md)', overflow: 'hidden', background: 'var(--bg)', display: 'grid', placeItems: 'center' }}>
            {heroImageUrl
              ? <img src={`${API_URL}/${heroImageUrl}`} alt="รูปปกปัจจุบัน" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              : <span className="muted" style={{ fontSize: 13, textAlign: 'center', padding: 12 }}>ยังไม่ได้ตั้งรูปปก<br />ใช้รูปสินค้าอัตโนมัติ</span>}
          </div>
          <div style={{ flex: 1, minWidth: 220 }}>
            <h2 id="hero-h" style={{ margin: 0 }}>รูปปกหน้า home</h2>
            <p className="muted" style={{ margin: '4px 0 16px', fontSize: 14 }}>รูปใหญ่ด้านขวาของแบนเนอร์ที่ลูกค้าเห็นหลัง login (jpg, png, webp)</p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden
                onChange={handleUpload} aria-label="เลือกรูปปก" />
              <button className="btn btn-primary" onClick={() => fileRef.current?.click()} disabled={uploading}>
                <Icon name="upload" size="sm" />{uploading ? 'กำลังอัปโหลด…' : heroImageUrl ? 'เปลี่ยนรูป' : 'อัปโหลดรูป'}
              </button>
              {heroImageUrl && (
                <button className="btn btn-outline" onClick={handleRemoveHero}>ใช้รูปตั้งต้น</button>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── สินค้าแนะนำ ── */}
      <section className="card" aria-labelledby="feat-h">
        <div className="card-head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div>
            <h2 id="feat-h">สินค้าแนะนำ</h2>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: 13 }}>
              เลือกได้ {MAX_FEATURED} ช่อง — ลูกค้าเห็น {SHOWN} ชิ้นแรกที่เปิดขายและยังมีของ ตัวที่หมดหรือปิดการขายจะถูกข้าม
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {dirty && <span role="status" style={{ fontSize: 13, color: 'var(--amber)' }}>ยังไม่ได้บันทึก</span>}
            <button className="btn btn-primary" onClick={handleSave} disabled={!dirty || saving}>
              <Icon name="check" size="sm" />{saving ? 'กำลังบันทึก…' : 'บันทึก'}
            </button>
          </div>
        </div>

        <div className="pad">
          {/* ช่องตามลำดับ — หน้าตาเหมือนที่ลูกค้าเห็น */}
          {picked.length === 0 && (
            <p className="muted" style={{ fontSize: 14, margin: '0 0 12px' }}>
              ยังไม่ได้เลือก — ลูกค้าจะเห็นสินค้าขายดีอัตโนมัติ กดเลือกสินค้าจากด้านล่างได้เลย
            </p>
          )}
          <ol className="sf-slots" aria-label="สินค้าแนะนำที่เลือก">
            {Array.from({ length: MAX_FEATURED }).map((_, i) => {
              const id = picked[i];
              const p = id != null ? byId[id] : null;
              if (id == null) {
                return (
                  <li key={`empty-${i}`} className="sf-slot empty" aria-hidden="true">
                    <span className="sf-rank">{i + 1}</span>
                    <span>{i < SHOWN ? 'ว่าง — ใช้สินค้าขายดีแทน' : 'ว่าง'}</span>
                  </li>
                );
              }
              const closed = p?.isActive === false;
              const soldOut = p?.stock === 0;
              const status = closed ? ['ปิดการขาย — ลูกค้าไม่เห็น', '']
                : soldOut ? ['หมด — ลูกค้าไม่เห็น', 'b-red']
                : visibleToCustomers.includes(id) ? ['ลูกค้าเห็น', 'b-green']
                : ['สำรอง', ''];
              return (
                <li key={id} className={`sf-slot${soldOut || closed ? ' out' : ''}`}>
                  <div className="sf-img">
                    {p?.imageUrl ? <img src={`${API_URL}/${p.imageUrl}`} alt="" /> : <span>ไม่มีรูป</span>}
                    <span className="sf-rank">{i + 1}</span>
                  </div>
                  <div className="sf-meta">
                    <b title={p?.name}>{p?.name ?? `สินค้า #${id}`}</b>
                    <span className={`badge plain ${status[1]}`}>{status[0]}</span>
                  </div>
                  <div className="sf-actions">
                    <button className="icon-btn sm" onClick={() => move(i, -1)} disabled={i === 0} aria-label={`เลื่อน ${p?.name} ขึ้น`} title="เลื่อนไปก่อน"><Icon name="left" size="sm" /></button>
                    <button className="icon-btn sm" onClick={() => move(i, 1)} disabled={i === picked.length - 1} aria-label={`เลื่อน ${p?.name} ลง`} title="เลื่อนไปหลัง"><Icon name="right" size="sm" /></button>
                    <button className="icon-btn sm" onClick={() => remove(id)} aria-label={`เอา ${p?.name} ออก`} title="เอาออก" style={{ color: 'var(--red)', marginLeft: 'auto' }}><Icon name="x" size="sm" /></button>
                  </div>
                </li>
              );
            })}
          </ol>

          {/* เลือกสินค้า — การ์ดมีรูป กดเพื่อเพิ่ม/เอาออก */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap', margin: '28px 0 12px' }}>
            <h3 style={{ fontSize: 15, margin: 0 }}>เลือกสินค้า <span className="muted" style={{ fontWeight: 400 }}>({picked.length}/{MAX_FEATURED})</span></h3>
            <div className="field" style={{ margin: 0, width: 260, maxWidth: '100%' }}>
              <div className="control"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="ค้นหาชื่อสินค้า" aria-label="ค้นหาสินค้า" /></div>
            </div>
          </div>
          <ul className="sf-pick" aria-label="สินค้าทั้งหมด">
            {matches.map(p => {
              const rank = picked.indexOf(p.id);
              const selected = rank >= 0;
              const full = !selected && picked.length >= MAX_FEATURED;
              return (
                <li key={p.id}>
                  <button className={`sf-card${selected ? ' on' : ''}`} onClick={() => (selected ? remove(p.id) : add(p.id))}
                    disabled={full} aria-pressed={selected}
                    aria-label={selected ? `เอา ${p.name} ออก (อันดับ ${rank + 1})` : `เพิ่ม ${p.name}`}>
                    <div className="sf-img">
                      {p.imageUrl ? <img src={`${API_URL}/${p.imageUrl}`} alt="" /> : <span>ไม่มีรูป</span>}
                      {selected && <span className="sf-rank on"><Icon name="check" size="sm" />{rank + 1}</span>}
                    </div>
                    <div className="sf-meta">
                      <b title={p.name}>{p.name}</b>
                      <span className="sub">฿{Number(p.price).toLocaleString()} · {p.isActive === false ? 'ปิดการขาย'
                        : p.stock === 0 ? <span style={{ color: 'var(--red)' }}>หมด</span> : `เหลือ ${p.stock}`}</span>
                    </div>
                  </button>
                </li>
              );
            })}
            {matches.length === 0 && <li className="muted" style={{ fontSize: 14 }}>ไม่พบสินค้า</li>}
          </ul>
        </div>
      </section>

      <style>{`
        .sf-slots{list-style:none;padding:0;margin:0;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
        .sf-slot{position:relative;display:flex;flex-direction:column;border:1px solid var(--line);border-radius:var(--r-md);overflow:hidden;background:var(--surface);min-width:0}
        .sf-slot.empty{border-style:dashed;align-items:center;justify-content:center;gap:6px;min-height:180px;color:var(--faint);font-size:13px;background:var(--bg);text-align:center;padding:12px}
        .sf-slot.empty .sf-rank{position:static}
        .sf-slot.out .sf-img img{opacity:.45;filter:grayscale(.6)}
        .sf-img{position:relative;aspect-ratio:4/3;background:var(--bg);display:grid;place-items:center;color:var(--faint);font-size:12px}
        .sf-img img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
        .sf-rank{position:absolute;top:8px;left:8px;min-width:24px;height:24px;padding:0 6px;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;gap:2px;background:var(--ink);color:#fff;font-size:12px;font-weight:700}
        .sf-rank.on{background:var(--blue)}
        .sf-meta{display:flex;flex-direction:column;gap:4px;padding:10px 12px 4px;min-width:0;text-align:left}
        .sf-meta b{font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
        .sf-meta .badge{align-self:flex-start}
        .sf-actions{display:flex;gap:4px;padding:4px 8px 8px}
        .sf-pick{list-style:none;padding:0;margin:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}
        .sf-card{width:100%;padding:0;border:2px solid var(--line);border-radius:var(--r-md);overflow:hidden;background:var(--surface);cursor:pointer;color:inherit;font:inherit;transition:border-color .15s,box-shadow .15s}
        .sf-card:hover:not(:disabled){border-color:var(--line-2)}
        .sf-card.on{border-color:var(--blue);box-shadow:0 0 0 3px var(--blue-soft)}
        .sf-card:disabled{opacity:.5;cursor:not-allowed}
        .sf-card .sf-meta{padding-bottom:10px}
        @media (max-width:760px){.sf-slots{grid-template-columns:repeat(2,minmax(0,1fr))}}
      `}</style>
    </>
  );
}
