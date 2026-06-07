/**
 * exportCSV — แปลง array of objects เป็นไฟล์ .csv แล้ว download ทันที
 *
 * @param {Object[]} rows    - ข้อมูลที่จะ export
 * @param {string[]} headers - หัวคอลัมน์ภาษาไทย (ลำดับตรงกับ keys)
 * @param {string[]} keys    - key ของ object ที่จะดึง (ลำดับตรงกับ headers)
 * @param {string}   filename - ชื่อไฟล์ (ไม่ต้องใส่ .csv)
 */
export function exportCSV(rows, headers, keys, filename = 'export') {
  // 1. หัวตาราง
  const headerRow = headers.join(',');

  // 2. แต่ละแถวข้อมูล — escape ค่าที่มีลูกน้ำหรือ newline ด้วย double quote
  const dataRows = rows.map(row =>
    keys.map(key => {
      const val = row[key] ?? '';
      const str = String(val).replace(/"/g, '""'); // escape double quote
      return str.includes(',') || str.includes('\n') || str.includes('"')
        ? `"${str}"`
        : str;
    }).join(',')
  );

  // 3. รวมเป็น CSV string + BOM (﻿) ให้ Excel อ่านภาษาไทยถูก
  const csv = '﻿' + [headerRow, ...dataRows].join('\n');

  // 4. สร้าง Blob แล้ว trigger download
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url  = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href     = url;
  link.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
