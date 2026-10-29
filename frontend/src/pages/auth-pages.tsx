import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Check, Cloud, Eye, EyeOff, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { authApi } from '@/lib/api'
import { useAuth } from '@/components/auth-provider'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'

const passwordSchema = z.string().min(8, 'Use at least 8 characters').max(128, 'Use 128 characters or fewer').regex(/[a-z]/, 'Add a lowercase letter').regex(/[A-Z]/, 'Add an uppercase letter').regex(/[0-9]/, 'Add a number').regex(/[^A-Za-z0-9]/, 'Add a special character')
const emailSchema = z.string().trim().email('Enter a valid email address')
const loginSchema = z.object({ email: emailSchema, password: z.string().min(1, 'Enter your password') })
const registerSchema = z.object({ email: emailSchema, password: passwordSchema, confirmPassword: z.string() }).refine((values) => values.password === values.confirmPassword, { message: 'Passwords do not match', path: ['confirmPassword'] })
type LoginValues = z.infer<typeof loginSchema>
type RegisterValues = z.infer<typeof registerSchema>

function AuthFrame({ children, subtitle, title }: { children: React.ReactNode; subtitle: string; title: string }) {
  return <main className="grid min-h-screen lg:grid-cols-[1fr_1fr]">
    <section className="relative hidden overflow-hidden bg-[#111a32] p-12 text-white lg:flex lg:flex-col lg:justify-between xl:p-16">
      <div className="absolute -right-28 -top-28 h-[440px] w-[440px] rounded-full border border-white/10" /><div className="absolute -right-12 -top-12 h-[300px] w-[300px] rounded-full border border-white/10" /><div className="absolute bottom-24 right-20 h-56 w-56 rounded-full bg-blue-500/15 blur-3xl" />
      <Link to="/login" className="relative inline-flex w-fit items-center gap-3 rounded-lg focus-visible:ring-2 focus-visible:ring-white"><span className="grid h-10 w-10 place-items-center rounded-xl bg-blue-500"><Cloud size={21} /></span><span className="text-lg font-semibold">ShardVault</span></Link>
      <div className="relative max-w-lg"><p className="mb-4 text-sm font-medium uppercase tracking-[.18em] text-blue-300">Private by design</p><h2 className="text-4xl font-semibold leading-tight xl:text-5xl">Your files, protected at every layer.</h2><p className="mt-5 max-w-md text-base leading-7 text-slate-300">Encrypted storage built for the things you want to keep close and keep safe.</p><div className="mt-9 flex items-center gap-3 text-sm text-slate-300"><ShieldCheck className="text-blue-300" size={19} /> End-to-end encrypted storage</div></div>
      <p className="relative text-xs text-slate-400">© {new Date().getFullYear()} ShardVault. Your privacy, always.</p>
    </section>
    <section className="flex min-h-screen flex-col justify-center px-5 py-12 sm:px-12 lg:px-16 xl:px-24">
      <Link to="/login" className="mb-10 inline-flex w-fit items-center gap-2 font-semibold lg:hidden"><span className="grid h-9 w-9 place-items-center rounded-xl bg-primary text-primary-foreground"><Cloud size={18} /></span>ShardVault</Link>
      <div className="mx-auto w-full max-w-[400px]"><div className="mb-8"><h1 className="text-3xl font-semibold tracking-tight">{title}</h1><p className="mt-2 text-sm text-muted-foreground">{subtitle}</p></div>{children}</div>
    </section>
  </main>
}

function FieldError({ children }: { children?: string }) { return children ? <p className="mt-1.5 text-xs font-medium text-destructive" role="alert">{children}</p> : null }

export function LoginPage() {
  const [visible, setVisible] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const { accept } = useAuth()
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors } } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })
  const submit = handleSubmit(async (values) => {
    setSubmitting(true)
    try { const response = await authApi.login(values.email, values.password); accept(response); navigate('/', { replace: true }) }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Unable to sign in. Check your details and try again.') }
    finally { setSubmitting(false) }
  })
  return <AuthFrame title="Welcome back" subtitle="Sign in to continue to your secure vault.">
    <form onSubmit={submit} className="space-y-5" noValidate>
      <div><Label htmlFor="email">Email address</Label><Input id="email" type="email" autoComplete="email" placeholder="you@example.com" aria-invalid={!!errors.email} aria-describedby={errors.email ? 'login-email-error' : undefined} className="mt-2 h-11" {...register('email')} /><div id="login-email-error"><FieldError>{errors.email?.message}</FieldError></div></div>
      <div><div className="flex items-center justify-between"><Label htmlFor="password">Password</Label></div><div className="relative mt-2"><Input id="password" type={visible ? 'text' : 'password'} autoComplete="current-password" aria-invalid={!!errors.password} aria-describedby={errors.password ? 'login-password-error' : undefined} className="h-11 pr-11" {...register('password')} /><button type="button" onClick={() => setVisible(!visible)} className="absolute right-3 top-1/2 -translate-y-1/2 rounded text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring" aria-label={visible ? 'Hide password' : 'Show password'}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button></div><div id="login-password-error"><FieldError>{errors.password?.message}</FieldError></div></div>
      <Button type="submit" className="h-11 w-full" disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'}</Button>
    </form>
    <p className="mt-7 text-center text-sm text-muted-foreground">New to ShardVault? <Link className="font-semibold text-primary hover:underline" to="/register">Create an account</Link></p>
    <p className="mt-10 flex items-center justify-center gap-2 text-xs text-muted-foreground"><ShieldCheck size={15} /> Your credentials are protected with Argon2id.</p>
  </AuthFrame>
}

export function RegisterPage() {
  const [visible, setVisible] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const { accept } = useAuth()
  const navigate = useNavigate()
  const { register, handleSubmit, watch, formState: { errors } } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), mode: 'onTouched' })
  const password = watch('password') || ''
  const checks = [/\p{Ll}/u, /\p{Lu}/u, /\d/, /[^A-Za-z0-9]/, /.{8,}/].map((rule) => rule.test(password))
  const strength = checks.filter(Boolean).length
  const submit = handleSubmit(async ({ email, password: pass }) => {
    setSubmitting(true)
    try { const response = await authApi.register(email, pass); accept(response); navigate('/', { replace: true }) }
    catch { toast.error('Unable to create your account. Check your details and try again.') }
    finally { setSubmitting(false) }
  })
  return <AuthFrame title="Create your account" subtitle="A safer home for all the files that matter.">
    <form onSubmit={submit} className="space-y-4" noValidate>
      <div><Label htmlFor="register-email">Email address</Label><Input id="register-email" type="email" autoComplete="email" placeholder="you@example.com" aria-invalid={!!errors.email} aria-describedby={errors.email ? 'register-email-error' : undefined} className="mt-2 h-11" {...register('email')} /><div id="register-email-error"><FieldError>{errors.email?.message}</FieldError></div></div>
      <div><Label htmlFor="register-password">Password</Label><div className="relative mt-2"><Input id="register-password" type={visible ? 'text' : 'password'} autoComplete="new-password" aria-invalid={!!errors.password} aria-describedby="password-help" className="h-11 pr-11" {...register('password')} /><button type="button" onClick={() => setVisible(!visible)} className="absolute right-3 top-1/2 -translate-y-1/2 rounded text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring" aria-label={visible ? 'Hide password' : 'Show password'}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button></div><div className="mt-3 space-y-2" id="password-help"><div className="flex items-center justify-between text-xs"><span className="text-muted-foreground">Password strength</span><span className="font-medium">{password ? ['Very weak', 'Weak', 'Fair', 'Good', 'Strong', 'Strong'][strength] : '—'}</span></div><Progress aria-label="Password strength" value={strength * 20} className="h-1.5" /><div className="grid grid-cols-2 gap-x-3 gap-y-1 text-[11px] text-muted-foreground">{['Lowercase letter', 'Uppercase letter', 'Number', 'Special character', '8+ characters'].map((label, i) => <span key={label} className={checks[i] ? 'text-emerald-600 dark:text-emerald-400' : ''}>{checks[i] && <Check className="mr-1 inline h-3 w-3" />}{label}</span>)}</div></div><FieldError>{errors.password?.message}</FieldError></div>
      <div><Label htmlFor="confirm-password">Confirm password</Label><Input id="confirm-password" type={visible ? 'text' : 'password'} autoComplete="new-password" aria-invalid={!!errors.confirmPassword} className="mt-2 h-11" {...register('confirmPassword')} /><FieldError>{errors.confirmPassword?.message}</FieldError></div>
      <Button type="submit" className="h-11 w-full" disabled={submitting}>{submitting ? 'Creating account…' : 'Create account'}</Button>
    </form>
    <p className="mt-6 text-center text-sm text-muted-foreground">Already have an account? <Link className="font-semibold text-primary hover:underline" to="/login">Sign in</Link></p>
  </AuthFrame>
}
