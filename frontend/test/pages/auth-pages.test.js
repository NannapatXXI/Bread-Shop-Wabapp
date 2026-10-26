import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fakeApi, fail, ok } from '../helpers/fakeApi';

const router = { push: vi.fn(), replace: vi.fn(), back: vi.fn() };
let searchParams = new URLSearchParams();
vi.mock('next/navigation', () => ({
  useRouter: () => router,
  useSearchParams: () => searchParams,
  usePathname: () => '/login',
}));
vi.mock('react-hot-toast', () => ({ default: { success: vi.fn(), error: vi.fn() } }));

const { default: LoginPage } = await import('@/app/(auth)/login/page');
const { default: RegisterPage } = await import('@/app/(auth)/register/page');
const { default: ForgotPasswordPage } = await import('@/app/(auth)/forgot-password/page');
const { default: VerifyEmailPage } = await import('@/app/(auth)/verify-email/page');
const { default: ResetPasswordPage } = await import('@/app/(auth)/reset-password/page');

beforeEach(() => {
  vi.clearAllMocks();
  searchParams = new URLSearchParams();
});

describe('Login page', () => {
  it('empty submit shows a validation message and sends nothing', async () => {
    const calls = fakeApi(() => ok(null));
    render(<LoginPage />);

    await userEvent.click(screen.getByRole('button', { name: 'เข้าสู่ระบบ' }));

    expect(screen.getByRole('alert')).toHaveTextContent('กรุณากรอก Email และ Password');
    expect(calls).toHaveLength(0);
  });

  it('sends usernameOrEmail + password and goes to /home on success', async () => {
    const calls = fakeApi(() => ok(null, 'เข้าสู่ระบบสำเร็จ'));
    render(<LoginPage />);

    await userEvent.type(screen.getByLabelText('ชื่อผู้ใช้หรืออีเมล'), 'alice');
    await userEvent.type(screen.getByLabelText('รหัสผ่าน'), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: 'เข้าสู่ระบบ' }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith('/home'));
    expect(calls).toEqual([{ method: 'post', url: '/api/v1/auth/login', body: { usernameOrEmail: 'alice', password: 'secret123' } }]);
  });

  it('shows the backend error message on wrong password', async () => {
    fakeApi(() => fail(401, 'Username หรือ Password ไม่ถูกต้อง'));
    render(<LoginPage />);

    await userEvent.type(screen.getByLabelText('ชื่อผู้ใช้หรืออีเมล'), 'alice');
    await userEvent.type(screen.getByLabelText('รหัสผ่าน'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'เข้าสู่ระบบ' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username หรือ Password ไม่ถูกต้อง');
    expect(router.push).not.toHaveBeenCalled();
  });

  it('Google account: shows the "use the Google button" hint for the message the backend actually sends', async () => {
    // backend (GlobalExceptionHandler) ตอบข้อความนี้จริง — ล็อกไว้ฝั่ง backend ที่ AuthControllerTest.login_googleAccount
    fakeApi(() => fail(400, 'GOOGLE_ACCOUNT'));
    render(<LoginPage />);

    await userEvent.type(screen.getByLabelText('ชื่อผู้ใช้หรืออีเมล'), 'g@example.com');
    await userEvent.type(screen.getByLabelText('รหัสผ่าน'), 'whatever');
    await userEvent.click(screen.getByRole('button', { name: 'เข้าสู่ระบบ' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('บัญชีนี้ใช้ Google Login');
  });

  it('Google button redirects to the URL the backend returns', async () => {
    fakeApi(() => ok({ url: 'https://accounts.google.com/o/oauth2/auth?x=1' }));
    const original = window.location;
    delete window.location;
    window.location = { href: 'http://localhost:3000/login', pathname: '/login' };
    try {
      render(<LoginPage />);
      await userEvent.click(screen.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }));
      await waitFor(() => expect(window.location.href).toBe('https://accounts.google.com/o/oauth2/auth?x=1'));
    } finally {
      window.location = original;
    }
  });
});

describe('Register page', () => {
  async function fill({ username = 'alice01', email = 'alice@example.com', password = 'secret123', confirm = password } = {}) {
    if (username) await userEvent.type(screen.getByLabelText('ชื่อผู้ใช้'), username);
    if (email) await userEvent.type(screen.getByLabelText('อีเมล'), email);
    if (password) await userEvent.type(screen.getByLabelText('รหัสผ่าน'), password);
    if (confirm) await userEvent.type(screen.getByLabelText('ยืนยันรหัสผ่าน'), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'สมัครสมาชิก' }));
  }

  it.each([
    [{ username: 'ab' }, 'ชื่อผู้ใช้ต้องมีความยาว 4-20 ตัวอักษร'],
    [{ username: 'alice!!' }, 'ไม่มีอักขระพิเศษ'],
    [{ password: 'abcdefgh', confirm: 'abcdefgh' }, 'ต้องมีตัวเลขหรือสัญลักษณ์'],
    [{ confirm: 'secret124' }, 'รหัสผ่านและยืนยันรหัสผ่านไม่ตรงกัน'],
  ])('rejects %o on the client without calling the API', async (input, message) => {
    const calls = fakeApi(() => ok(null));
    render(<RegisterPage />);

    await fill(input);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(calls).toHaveLength(0);
  });

  it('rejects a 7-character password like the backend does (RegisterRequest @Size(min = 8))', async () => {
    const calls = fakeApi(() => fail(400, 'รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร'));
    render(<RegisterPage />);

    await fill({ password: 'abcde1x', confirm: 'abcde1x' });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it('valid form posts username/email/password and returns to /login', async () => {
    const calls = fakeApi(() => ok(null, 'สมัครสมาชิกสำเร็จ'));
    render(<RegisterPage />);

    await fill();

    await waitFor(() => expect(router.push).toHaveBeenCalledWith('/login'));
    expect(calls[0]).toEqual({ method: 'post', url: '/api/v1/auth/register',
      body: { username: 'alice01', email: 'alice@example.com', password: 'secret123' } });
  });

  it('duplicate username: shows the backend message', async () => {
    fakeApi(() => fail(400, 'มีชื่อผู้ใช้นี้ในระบบแล้ว'));
    render(<RegisterPage />);

    await fill();

    expect(await screen.findByRole('alert')).toHaveTextContent('มีชื่อผู้ใช้นี้ในระบบแล้ว');
    expect(router.push).not.toHaveBeenCalled();
  });
});

describe('Forgot password page', () => {
  it('rejects a malformed email without calling the API', async () => {
    const calls = fakeApi(() => ok({ token: 't' }));
    render(<ForgotPasswordPage />);

    await userEvent.type(screen.getByLabelText('อีเมล'), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: 'ส่งรหัส OTP' }));

    expect(screen.getByRole('alert')).toHaveTextContent('รูปแบบอีเมลไม่ถูกต้อง');
    expect(calls).toHaveLength(0);
  });

  it('sends the email as a plain string (backend CheckEmailRequest(String email))', async () => {
    const calls = fakeApi(() => ok({ token: 'otp-token' }));
    render(<ForgotPasswordPage />);

    await userEvent.type(screen.getByLabelText('อีเมล'), '  alice@example.com ');
    await userEvent.click(screen.getByRole('button', { name: 'ส่งรหัส OTP' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toEqual({ method: 'post', url: '/api/v1/auth/otp', body: { email: 'alice@example.com' } });
  });

  it('goes to the OTP page with token and encoded email', async () => {
    fakeApi(() => ok({ token: 'otp-token' }));
    render(<ForgotPasswordPage />);

    await userEvent.type(screen.getByLabelText('อีเมล'), 'a+b@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'ส่งรหัส OTP' }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith('/verify-email?token=otp-token&email=a%2Bb%40example.com'));
  });
});

describe('Verify OTP page', () => {
  beforeEach(() => {
    searchParams = new URLSearchParams({ token: 'otp-token', email: 'alice@example.com' });
  });

  const boxes = () => screen.getAllByRole('textbox');

  it('shows the email, moves focus as digits are typed, and keeps the button disabled until 6 digits', async () => {
    fakeApi(() => ok({ token: 'reset-token' }));
    render(<VerifyEmailPage />);

    expect(await screen.findByText('alice@example.com')).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'ยืนยันรหัส' });

    await userEvent.click(boxes()[0]);
    await userEvent.keyboard('12345');
    expect(boxes()[5]).toHaveFocus();
    expect(submit).toBeDisabled();

    await userEvent.keyboard('6');
    expect(submit).toBeEnabled();
  });

  it('ignores letters', async () => {
    fakeApi(() => ok(null));
    render(<VerifyEmailPage />);

    await userEvent.click((await screen.findAllByRole('textbox'))[0]);
    await userEvent.keyboard('a');

    expect(boxes()[0]).toHaveValue('');
  });

  it('paste fills all boxes, and submit sends token + joined OTP then goes to reset', async () => {
    const calls = fakeApi(() => ok({ token: 'reset-token' }));
    render(<VerifyEmailPage />);

    await userEvent.click((await screen.findAllByRole('textbox'))[0]);
    await userEvent.paste('482913');
    expect(boxes().map((b) => b.value).join('')).toBe('482913');

    await userEvent.click(screen.getByRole('button', { name: 'ยืนยันรหัส' }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith('/reset-password?token=reset-token'));
    expect(calls[0]).toEqual({ method: 'post', url: '/api/v1/auth/otp/verification', body: { token: 'otp-token', otp: '482913' } });
  });

  it('resend sends the email as a plain string and resets the boxes', async () => {
    const calls = fakeApi(() => ok({ token: 'new-token' }));
    render(<VerifyEmailPage />);

    await userEvent.click((await screen.findAllByRole('textbox'))[0]);
    await userEvent.keyboard('12');
    await userEvent.click(screen.getByRole('button', { name: 'ส่งใหม่' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].body).toEqual({ email: 'alice@example.com' });
  });
});

describe('Reset password page', () => {
  beforeEach(() => {
    searchParams = new URLSearchParams({ token: 'reset-token' });
  });

  it('mismatch is caught on the client', async () => {
    const calls = fakeApi(() => ok(null));
    render(<ResetPasswordPage />);

    await userEvent.type(await screen.findByLabelText('รหัสผ่านใหม่'), 'brandNew99');
    await userEvent.type(screen.getByLabelText('ยืนยันรหัสผ่านใหม่'), 'brandNew98');
    await userEvent.click(screen.getByRole('button', { name: 'บันทึกรหัสผ่านใหม่' }));

    expect(screen.getByRole('alert')).toHaveTextContent('รหัสผ่านและยืนยันรหัสผ่านไม่ตรงกัน');
    expect(calls).toHaveLength(0);
  });

  it('sends token + newPassword', async () => {
    const calls = fakeApi(() => ok(null));
    render(<ResetPasswordPage />);

    await userEvent.type(await screen.findByLabelText('รหัสผ่านใหม่'), 'brandNew99');
    await userEvent.type(screen.getByLabelText('ยืนยันรหัสผ่านใหม่'), 'brandNew99');
    await userEvent.click(screen.getByRole('button', { name: 'บันทึกรหัสผ่านใหม่' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toEqual({ method: 'put', url: '/api/v1/auth/password', body: { token: 'reset-token', newPassword: 'brandNew99' } });
  });
});
