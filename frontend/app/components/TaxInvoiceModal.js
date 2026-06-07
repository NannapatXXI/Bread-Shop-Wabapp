'use client';

const SHOP = {
  name:    'Peak Pung By Mom Hmee',
  address: '123 ถนนสุขุมวิท แขวงคลองเตย เขตคลองเตย กรุงเทพมหานคร 10110',
  phone:   '02-xxx-xxxx',
  taxId:   '0-0000-00000-00-0',
};

const fmt = (n) =>
  Number(n ?? 0).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtDate = (d) =>
  new Date(d).toLocaleDateString('th-TH', { year: 'numeric', month: 'long', day: 'numeric' });

function buildPrintHtml(order) {
  const subtotal  = Number(order.subtotal      ?? 0);
  const discount  = Number(order.discountAmount ?? 0);
  const shipping  = Number(order.shippingFee    ?? 0);
  const total     = Number(order.totalAmount    ?? 0);
  const vatAmount = total * 7 / 107;
  const beforeVat = total - vatAmount;

  const rows = (order.orderLines ?? []).map((line, i) => `
    <tr style="background:${i % 2 === 0 ? '#ffffff' : '#f9fafb'}">
      <td style="padding:9px 12px;color:#374151;border-bottom:1px solid #f3f4f6">${line.productName}</td>
      <td style="padding:9px 12px;text-align:center;color:#6b7280;border-bottom:1px solid #f3f4f6">${line.quantity}</td>
      <td style="padding:9px 12px;text-align:right;color:#6b7280;border-bottom:1px solid #f3f4f6">฿${fmt(line.unitPrice)}</td>
      <td style="padding:9px 12px;text-align:right;font-weight:600;color:#111827;border-bottom:1px solid #f3f4f6">฿${fmt(line.totalPrice)}</td>
    </tr>`).join('');

  const discountRow = discount > 0 ? `
    <tr>
      <td colspan="3" style="padding:4px 0;text-align:right;color:#16a34a;font-size:13px">
        ส่วนลด${order.promotionCode ? ` (${order.promotionCode})` : ''}
      </td>
      <td style="padding:4px 0;text-align:right;color:#16a34a;font-size:13px;padding-left:24px">-฿${fmt(discount)}</td>
    </tr>` : '';

  return `<!DOCTYPE html>
<html lang="th">
<head>
  <meta charset="UTF-8"/>
  <title>ใบกำกับภาษี ${order.orderNo}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link href="https://fonts.googleapis.com/css2?family=Noto+Sans+Thai:wght@300;400;500;600;700&display=swap" rel="stylesheet"/>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Noto Sans Thai', sans-serif;
      font-size: 13px;
      color: #111827;
      background: #ffffff;
      padding: 48px 56px;
      max-width: 794px;
      margin: 0 auto;
    }
    @media print {
      body { padding: 32px 40px; }
    }
  </style>
</head>
<body>

  <!-- ═══ HEADER ═══ -->
  <table width="100%" style="margin-bottom:20px">
    <tr>
      <td style="vertical-align:top">
        <div style="font-size:20px;font-weight:700;color:#0B1F33;letter-spacing:-0.3px">${SHOP.name}</div>
        <div style="font-size:11px;color:#6b7280;margin-top:5px;line-height:1.8">
          ${SHOP.address}<br/>
          โทร ${SHOP.phone}&nbsp;&nbsp;·&nbsp;&nbsp;เลขประจำตัวผู้เสียภาษี ${SHOP.taxId}
        </div>
      </td>
      <td style="vertical-align:top;text-align:right;min-width:180px">
        <div style="font-size:17px;font-weight:700;color:#0B1F33">ใบกำกับภาษีอย่างย่อ</div>
        <div style="font-size:11px;color:#9ca3af;margin-top:4px;letter-spacing:0.3px">TAX INVOICE (Abbreviated)</div>
      </td>
    </tr>
  </table>

  <!-- ═══ DIVIDER ═══ -->
  <div style="border-top:2px solid #0B1F33;margin-bottom:20px"></div>

  <!-- ═══ META ═══ -->
  <table width="100%" style="margin-bottom:24px">
    <tr>
      <td style="vertical-align:top;width:55%">
        <div style="font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:0.8px;margin-bottom:6px">ผู้ซื้อ</div>
        <div style="font-size:14px;font-weight:600;color:#111827;margin-bottom:4px">${order.shippingName}</div>
        <div style="font-size:12px;color:#6b7280;line-height:1.8">
          ${order.shippingAddress} ${order.shippingSubdistrict ?? ''}<br/>
          ${order.shippingDistrict ?? ''} ${order.shippingProvince ?? ''} ${order.shippingPostcode ?? ''}<br/>
          โทร ${order.shippingPhone}
        </div>
      </td>
      <td style="vertical-align:top;text-align:right">
        <table style="margin-left:auto">
          <tr>
            <td style="padding-bottom:12px">
              <div style="font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:0.8px;margin-bottom:4px">เลขที่ใบกำกับภาษี</div>
              <div style="font-size:14px;font-weight:700;color:#0B1F33;font-family:monospace">${order.orderNo}</div>
            </td>
          </tr>
          <tr>
            <td>
              <div style="font-size:10px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:0.8px;margin-bottom:4px">วันที่ออกเอกสาร</div>
              <div style="font-size:13px;color:#374151">${fmtDate(order.createdAt)}</div>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>

  <!-- ═══ ITEMS TABLE ═══ -->
  <table width="100%" style="border-collapse:collapse;margin-bottom:20px">
    <thead>
      <tr style="background:#0B1F33">
        <th style="padding:10px 12px;text-align:left;color:#ffffff;font-size:11px;font-weight:600;letter-spacing:0.5px;border-radius:6px 0 0 0">รายการสินค้า</th>
        <th style="padding:10px 12px;text-align:center;color:#ffffff;font-size:11px;font-weight:600;letter-spacing:0.5px;width:80px">จำนวน</th>
        <th style="padding:10px 12px;text-align:right;color:#ffffff;font-size:11px;font-weight:600;letter-spacing:0.5px;width:120px">ราคา/หน่วย</th>
        <th style="padding:10px 12px;text-align:right;color:#ffffff;font-size:11px;font-weight:600;letter-spacing:0.5px;width:120px;border-radius:0 6px 0 0">รวม</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>

  <!-- ═══ SUMMARY ═══ -->
  <table style="margin-left:auto;width:280px;margin-bottom:40px">
    <tr>
      <td style="padding:5px 0;color:#6b7280;font-size:13px">ราคาสินค้า</td>
      <td style="padding:5px 0;text-align:right;color:#374151;font-size:13px">฿${fmt(subtotal)}</td>
    </tr>
    ${discount > 0 ? `
    <tr>
      <td style="padding:5px 0;color:#16a34a;font-size:13px">ส่วนลด${order.promotionCode ? ` (${order.promotionCode})` : ''}</td>
      <td style="padding:5px 0;text-align:right;color:#16a34a;font-size:13px">-฿${fmt(discount)}</td>
    </tr>` : ''}
    <tr>
      <td style="padding:5px 0;color:#6b7280;font-size:13px">ค่าจัดส่ง</td>
      <td style="padding:5px 0;text-align:right;color:#374151;font-size:13px">฿${fmt(shipping)}</td>
    </tr>
    <tr>
      <td colspan="2" style="padding-top:10px">
        <div style="border-top:1px dashed #d1d5db;margin-bottom:10px"></div>
      </td>
    </tr>
    <tr>
      <td style="padding:3px 0;color:#9ca3af;font-size:11px">มูลค่าก่อนภาษี</td>
      <td style="padding:3px 0;text-align:right;color:#9ca3af;font-size:11px">฿${fmt(beforeVat)}</td>
    </tr>
    <tr>
      <td style="padding:3px 0;color:#9ca3af;font-size:11px">ภาษีมูลค่าเพิ่ม 7%</td>
      <td style="padding:3px 0;text-align:right;color:#9ca3af;font-size:11px">฿${fmt(vatAmount)}</td>
    </tr>
    <tr>
      <td colspan="2" style="padding-top:10px">
        <div style="border-top:2px solid #0B1F33;margin-bottom:10px"></div>
      </td>
    </tr>
    <tr>
      <td style="padding:2px 0;font-size:15px;font-weight:700;color:#0B1F33">รวมทั้งสิ้น</td>
      <td style="padding:2px 0;text-align:right;font-size:15px;font-weight:700;color:#0B1F33">฿${fmt(total)}</td>
    </tr>
  </table>

  <!-- ═══ SIGNATURE ═══ -->
  <table width="100%" style="margin-top:16px">
    <tr>
      <td style="width:45%;text-align:center;padding-top:48px">
        <div style="border-top:1px solid #d1d5db;padding-top:8px">
          <div style="font-size:11px;color:#6b7280">ผู้รับสินค้า / ลูกค้า</div>
          <div style="font-size:11px;color:#9ca3af;margin-top:3px">${order.shippingName}</div>
        </div>
      </td>
      <td style="width:10%"></td>
      <td style="width:45%;text-align:center;padding-top:48px">
        <div style="border-top:1px solid #d1d5db;padding-top:8px">
          <div style="font-size:11px;color:#6b7280">ผู้มีอำนาจลงนาม</div>
          <div style="font-size:11px;color:#9ca3af;margin-top:3px">${SHOP.name}</div>
        </div>
      </td>
    </tr>
  </table>

  <!-- ═══ FOOTER NOTE ═══ -->
  <div style="text-align:center;margin-top:40px;font-size:10px;color:#d1d5db">
    เอกสารนี้ออกโดยระบบอัตโนมัติ &nbsp;·&nbsp; ${SHOP.name} &nbsp;·&nbsp; เลขประจำตัวผู้เสียภาษี ${SHOP.taxId}
  </div>

</body>
</html>`;
}

export default function TaxInvoiceModal({ order, onClose }) {
  if (!order) return null;

  const subtotal  = Number(order.subtotal      ?? 0);
  const discount  = Number(order.discountAmount ?? 0);
  const shipping  = Number(order.shippingFee    ?? 0);
  const total     = Number(order.totalAmount    ?? 0);
  const vatAmount = total * 7 / 107;
  const beforeVat = total - vatAmount;

  const handlePrint = () => {
    const html = buildPrintHtml(order);
    const w = window.open('', '_blank', 'width=860,height=1100');
    w.document.write(html);
    w.document.close();
    w.focus();
    setTimeout(() => { w.print(); }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col">

        {/* Toolbar */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-800">ใบกำกับภาษีอย่างย่อ — {order.orderNo}</h2>
          <div className="flex gap-2">
            <button
              onClick={handlePrint}
              className="px-4 py-1.5 rounded-lg bg-[#0B1F33] text-white text-sm font-medium hover:bg-[#162d47] transition"
            >
              พิมพ์ / บันทึก PDF
            </button>
            <button
              onClick={onClose}
              className="px-4 py-1.5 rounded-lg border border-gray-200 text-gray-600 text-sm hover:bg-gray-50 transition"
            >
              ปิด
            </button>
          </div>
        </div>

        {/* Preview */}
        <div className="overflow-y-auto p-6">

          {/* Header */}
          <div className="flex justify-between items-start mb-4">
            <div>
              <p className="text-base font-bold text-[#0B1F33]">{SHOP.name}</p>
              <p className="text-[11px] text-gray-400 mt-1 leading-relaxed">
                {SHOP.address}<br/>
                โทร {SHOP.phone} · เลขผู้เสียภาษี {SHOP.taxId}
              </p>
            </div>
            <div className="text-right">
              <p className="text-sm font-bold text-[#0B1F33]">ใบกำกับภาษีอย่างย่อ</p>
              <p className="text-[10px] text-gray-400 mt-0.5 tracking-wide">TAX INVOICE (Abbreviated)</p>
            </div>
          </div>

          <hr className="border-t-2 border-[#0B1F33] mb-4" />

          {/* Meta */}
          <div className="flex justify-between mb-5">
            <div>
              <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1">ผู้ซื้อ</p>
              <p className="text-sm font-semibold text-gray-800">{order.shippingName}</p>
              <p className="text-xs text-gray-500 leading-relaxed mt-0.5">
                {order.shippingAddress} {order.shippingSubdistrict}<br/>
                {order.shippingDistrict} {order.shippingProvince} {order.shippingPostcode}<br/>
                โทร {order.shippingPhone}
              </p>
            </div>
            <div className="text-right shrink-0 ml-4">
              <div className="mb-3">
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1">เลขที่ใบกำกับภาษี</p>
                <p className="text-sm font-bold text-[#0B1F33] font-mono">{order.orderNo}</p>
              </div>
              <div>
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-1">วันที่ออกเอกสาร</p>
                <p className="text-sm text-gray-700">{fmtDate(order.createdAt)}</p>
              </div>
            </div>
          </div>

          {/* Items table */}
          <table className="w-full text-sm mb-5 border-collapse">
            <thead>
              <tr className="bg-[#0B1F33] text-white text-xs">
                <th className="text-left py-2.5 px-3 font-semibold tracking-wide">รายการสินค้า</th>
                <th className="text-center py-2.5 px-3 font-semibold tracking-wide w-16">จำนวน</th>
                <th className="text-right py-2.5 px-3 font-semibold tracking-wide w-28">ราคา/หน่วย</th>
                <th className="text-right py-2.5 px-3 font-semibold tracking-wide w-28">รวม</th>
              </tr>
            </thead>
            <tbody>
              {order.orderLines?.map((line, i) => (
                <tr key={line.id ?? i} className={i % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                  <td className="py-2 px-3 text-gray-700 border-b border-gray-100">{line.productName}</td>
                  <td className="py-2 px-3 text-center text-gray-500 border-b border-gray-100">{line.quantity}</td>
                  <td className="py-2 px-3 text-right text-gray-500 border-b border-gray-100">฿{fmt(line.unitPrice)}</td>
                  <td className="py-2 px-3 text-right font-semibold text-gray-800 border-b border-gray-100">฿{fmt(line.totalPrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Summary */}
          <div className="ml-auto w-64 text-sm">
            <div className="flex justify-between py-1 text-gray-500">
              <span>ราคาสินค้า</span><span>฿{fmt(subtotal)}</span>
            </div>
            {discount > 0 && (
              <div className="flex justify-between py-1 text-green-600">
                <span>ส่วนลด{order.promotionCode ? ` (${order.promotionCode})` : ''}</span>
                <span>-฿{fmt(discount)}</span>
              </div>
            )}
            <div className="flex justify-between py-1 text-gray-500">
              <span>ค่าจัดส่ง</span><span>฿{fmt(shipping)}</span>
            </div>
            <div className="border-t border-dashed border-gray-200 my-2" />
            <div className="flex justify-between py-0.5 text-xs text-gray-400">
              <span>มูลค่าก่อนภาษี</span><span>฿{fmt(beforeVat)}</span>
            </div>
            <div className="flex justify-between py-0.5 text-xs text-gray-400">
              <span>ภาษีมูลค่าเพิ่ม 7%</span><span>฿{fmt(vatAmount)}</span>
            </div>
            <div className="border-t-2 border-[#0B1F33] mt-2 pt-2 flex justify-between font-bold text-[#0B1F33] text-base">
              <span>รวมทั้งสิ้น</span><span>฿{fmt(total)}</span>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
