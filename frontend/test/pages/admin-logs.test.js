import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { fakeApi, ok } from '../helpers/fakeApi';

vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const { default: AdminLogsPage } = await import('@/app/admin/logs/page');

const summary = {
  totalActivity: 57, totalAudit: 8, loginCount: 45, registerCount: 6,
  activeUsersToday: 2,
};

describe('Admin logs page (system logs + API stats moved to Grafana)', () => {
  it('keeps only the business-log tabs — no System / API / Errors tabs', () => {
    fakeApi(() => ok(summary));
    render(<AdminLogsPage />);

    const tabs = screen.getAllByRole('tab').map((t) => t.textContent);
    expect(tabs).toEqual(['ภาพรวม', 'User Activity', 'Audit', 'Order']);
  });

  it('overview shows the business numbers and links to the Grafana dashboard instead of log-file charts', async () => {
    const calls = fakeApi(() => ok(summary));
    render(<AdminLogsPage />);

    expect(await screen.findByText('57')).toBeInTheDocument();
    expect(screen.queryByText('System Errors')).not.toBeInTheDocument();
    expect(screen.queryByText(/System Log วันนี้/)).not.toBeInTheDocument();
    expect(screen.queryByText(/API Requests|API Errors/)).not.toBeInTheDocument();

    const link = screen.getByRole('link', { name: /เปิด Grafana/ });
    expect(link).toHaveAttribute('href', 'http://localhost:3001/d/breadshop-backend');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));

    // ไม่เรียก endpoint ที่ลบไปแล้ว
    const urls = calls.map((c) => c.url);
    expect(urls).toContain('/api/v1/admin/logs/summary');
    expect(urls.some((u) => /\/logs\/(system|trend|error-trend|api-stats|failed-requests|error-breakdown)/.test(u))).toBe(false);
    expect(within(screen.getByRole('tablist')).queryByText('System')).toBeNull();
  });
});
