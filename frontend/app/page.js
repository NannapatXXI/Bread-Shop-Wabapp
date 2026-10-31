'use client';

import Link from 'next/link';
import Brand from './components/Brand';
import Icon from './components/Icon';
import LandingStory from './components/LandingStory';
import { useEffect, useState } from 'react';
import { getStorefront } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export default function LandingPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // สินค้าแนะนำที่ admin เลือก (เฉพาะที่มีของ) — ไม่ได้เลือกก็เป็นของขายดีจริง
    getStorefront()
      .then(res => setProducts(Array.isArray(res.data?.featured) ? res.data.featured : []))
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="landing">
      <header className="top">
        <div className="wrap">
          <Brand href="/" />
          <nav className="main" aria-label="เมนู">
            <a href="#menu">เมนูขายดี</a>
            <a href="#how">วิธีสั่ง</a>
            <a href="#contact">ติดต่อร้าน</a>
          </nav>
          <div className="tools">
            <Link className="btn btn-ghost sm l-login" href="/login">เข้าสู่ระบบ</Link>
            <Link className="btn btn-primary sm" href="/register">สมัครสมาชิก</Link>
          </div>
        </div>
      </header>

      <main>
        <section className="l-hero">
          <div className="wrap l-hero-in">
            <div className="l-copy">
              <span className="l-eyebrow">อบใหม่ทุกเช้า · สั่งล่วงหน้าได้</span>
              <h1>ขนมอบโฮมเมด<br />จากครัวของแม่หมี</h1>
              <p>เค้ก ขนมปัง และโดนัท อบใหม่ทุกวัน สั่งออนไลน์แล้วรอรับถึงบ้าน</p>
              <div className="l-cta">
                <Link className="btn btn-primary" href="/register">เริ่มสั่งขนม <Icon name="right" size="sm" /></Link>
                <a className="btn btn-ghost-dark" href="#menu">ดูเมนู</a>
              </div>
              <ul className="l-points">
                <li><Icon name="check" size="sm" />สั่งล่วงหน้าออนไลน์ได้</li>
                <li><Icon name="check" size="sm" />ติดตามสถานะออเดอร์ได้</li>
              </ul>
            </div>
            <LandingStory />
          </div>
        </section>

        <section id="menu" className="wrap l-sec" style={{ scrollMarginTop: 64 }}>
          <div className="sec-head">
            <div><span className="l-kicker">เมนูขายดี</span><h2>ขนมที่ลูกค้าสั่งบ่อย</h2></div>
            <Link className="link" href="/login" style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 14 }}>
              ดูเมนูทั้งหมด <Icon name="right" size="sm" />
            </Link>
          </div>
          {!loading && products.length === 0 ? (
            <div className="empty">
              <div className="ic"><Icon name="shop" /></div>
              <h3>เมนูกำลังจะมาเร็วๆ นี้</h3>
              <p>สมัครสมาชิกไว้ก่อน แล้วสั่งได้ทันทีเมื่อขนมพร้อมขาย</p>
            </div>
          ) : (
            <div className="pgrid">
              {loading
                ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i}><div className="sk" style={{ aspectRatio: '1/1', borderRadius: 'var(--r-md)' }} /><div className="sk" style={{ height: 14, width: '60%', marginTop: 12 }} /></div>
                ))
                : products.map(product => (
                  <Link key={product.id} href="/login" className="pcard">
                    <div className="ph">
                      {product.imageUrl
                        ? <img src={`${API_URL}/${product.imageUrl}`} alt={product.name} />
                        : <span className="ph-empty">ไม่มีรูปสินค้า</span>}
                    </div>
                    <div className="pmeta">
                      <div style={{ minWidth: 0 }}>
                        <h3>{product.name}</h3>
                        <p className="desc">{product.description || product.category}</p>
                        <div className="price">฿{product.price}</div>
                      </div>
                    </div>
                  </Link>
                ))}
            </div>
          )}
        </section>

        <section id="how" className="l-how" style={{ scrollMarginTop: 64 }}>
          <div className="wrap">
            <span className="l-kicker">วิธีสั่ง</span><h2>สั่งง่ายใน 3 ขั้นตอน</h2>
            <ol className="l-steps">
              <li><span className="n">1</span><Icon name="shop" /><h3>เลือกขนม</h3><p>ดูเมนูที่อบในวันนี้ แล้วใส่ตะกร้า</p></li>
              <li><span className="n">2</span><Icon name="receipt" /><h3>ยืนยันคำสั่งซื้อ</h3><p>ใส่ที่อยู่ ใช้โค้ดส่วนลดถ้ามี แล้วยืนยัน</p></li>
              <li><span className="n">3</span><Icon name="home" /><h3>รับขนม</h3><p>รอรับขนมถึงบ้าน ติดตามสถานะได้ตลอด</p></li>
            </ol>
          </div>
        </section>

        <section className="wrap l-sec">
          <div className="l-band">
            <div>
              <h2>สมัครสมาชิก แล้วเริ่มสั่งได้เลย</h2>
              <p>บันทึกที่อยู่ไว้ครั้งเดียว ครั้งต่อไปสั่งได้ในไม่กี่คลิก</p>
            </div>
            <div className="l-cta">
              <Link className="btn btn-primary" href="/register">สมัครสมาชิกฟรี</Link>
              <Link className="btn btn-ghost-dark" href="/login">มีบัญชีแล้ว</Link>
            </div>
          </div>
        </section>
      </main>

      <footer id="contact" className="l-foot">
        <div className="wrap l-foot-in">
          <div>
            <Brand href="/" />
            <p className="muted" style={{ maxWidth: 280, fontSize: 14, margin: '12px 0 0' }}>ขนมอบโฮมเมด อบใหม่ทุกวัน</p>
          </div>
          <div>
            <h3>ติดต่อร้าน</h3>
            <ul>
              <li><Icon name="phone" size="sm" />โทร 085-427-4844</li>
              <li><Icon name="chat" size="sm" />LINE: sari_mee1980</li>
            </ul>
          </div>
          <div>
            <h3>บัญชี</h3>
            <ul>
              <li><Link href="/login">เข้าสู่ระบบ</Link></li>
              <li><Link href="/register">สมัครสมาชิก</Link></li>
              <li><Link href="/forgot-password">ลืมรหัสผ่าน</Link></li>
            </ul>
          </div>
        </div>
        <div className="wrap l-copy-r">© Peak Pung by Mom Hmee</div>
      </footer>

      <div className="l-sticky"><Link className="btn btn-primary btn-block" href="/register">เริ่มสั่งขนม</Link></div>
    </div>
  );
}
