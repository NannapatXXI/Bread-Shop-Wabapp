"use client";

// [Claude] CategoryDonutChart — ดึงยอดขายแบ่งตาม category จาก backend แทน hardcode

import { useEffect, useState } from "react";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts";
import api from "@/lib/api";

const COLORS = ["#1E3A8A", "#3B82F6", "#60A5FA", "#BFDBFE", "#93C5FD"];

export default function CategoryDonutChart() {
  const [data, setData] = useState([]);

  useEffect(() => {
    api.get("/api/v1/admin/dashboard/category-sales")
      .then((res) => setData(res.data.data))
      .catch(() => setData([]));
  }, []);

  const total = data.reduce((sum, item) => sum + Number(item.value), 0);

  if (data.length === 0) {
    return (
      <div className="w-full h-full flex items-center justify-center">
        <p className="text-gray-400 text-sm text-center">ใน 30 วันที่ผ่านมาไม่มี order</p>
      </div>
    );
  }

  return (
    <div className="w-full h-full flex flex-col md:flex-row items-center justify-between">

      {/* วงกลม */}
      <div className="w-[280px] h-[280px] relative">
        <ResponsiveContainer>
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              innerRadius={70}
              outerRadius={100}
              paddingAngle={5}
              cornerRadius={10}
            >
              {data.map((_, index) => (
                <Cell key={index} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip formatter={(v) => `฿${Number(v).toLocaleString('th-TH')}`} />
          </PieChart>
        </ResponsiveContainer>

        {/* ตัวเลขตรงกลาง */}
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <h1 className="text-3xl font-bold">฿{Number(total).toLocaleString('th-TH')}</h1>
          <p className="text-gray-400 text-sm">รวม</p>
        </div>
      </div>

      {/* Legend + ราคา */}
      <div className="space-y-4 w-full md:w-1/2 mt-6 md:mt-0">
        {data.map((item, index) => {
          const percent = total > 0 ? ((Number(item.value) / total) * 100).toFixed(0) : 0;
          return (
            <div key={index} className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-4 h-4 rounded-md" style={{ backgroundColor: COLORS[index % COLORS.length] }} />
                <span className="text-gray-600 font-medium">{item.name}</span>
              </div>
              <div className="text-right">
                <p className="font-bold">฿{Number(item.value).toLocaleString('th-TH')}</p>
                <p className="text-gray-400 text-sm">{percent}%</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
