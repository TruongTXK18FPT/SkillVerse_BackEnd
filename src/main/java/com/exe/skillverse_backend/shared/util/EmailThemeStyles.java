package com.exe.skillverse_backend.shared.util;

/**
 * Shared CSS theme for transactional / system emails.
 *
 * The palette mirrors the canonical "booking confirmation" email
 * (dark cyan-blue header, soft blue card body, cyan accents).
 *
 * Usage (recommended): pass {@link #CSS_BLOCK} as the FIRST {@code %s}
 * argument of a {@code String.format(...)} HTML template, so that the
 * single-percent CSS values do not have to be escaped:
 *
 * <pre>{@code
 * String html = String.format("""
 *     <!DOCTYPE html>
 *     <html>
 *     <head>
 *       <meta charset="UTF-8">
 *       <style>%s</style>
 *     </head>
 *     <body>
 *       ...body using %s, %s ...
 *     </body>
 *     </html>
 *     """, EmailThemeStyles.CSS_BLOCK, name, amount);
 * }</pre>
 *
 * The CSS intentionally covers the union of class names used across
 * the existing email builders (wallet, premium, otp, welcome,
 * violation report, course purchase, payment, student verification),
 * so individual templates only need to swap their {@code <style>}
 * block to a single {@code %s} placeholder.
 */
public final class EmailThemeStyles {

    private EmailThemeStyles() {
        // utility class
    }

    /**
     * Full CSS rules (without surrounding {@code <style>} tags).
     * Contains plain {@code %} characters; intended to be supplied as
     * a {@code %s} argument to {@link String#format(String, Object...)}.
     */
    public static final String CSS_BLOCK = """
            body { font-family: 'Inter', 'Segoe UI', 'Roboto', 'Arial', sans-serif; background:#f3f6fb; color:#132238; margin:0; padding:0; line-height:1.6; }
            .wrapper { width:100%; background:#f3f6fb; padding:24px 12px; }
            .container { max-width:640px; margin:0 auto; background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 10px 25px rgba(15,59,99,0.08); border:1px solid #d9e4f1; }
            .header { padding:32px 24px; background:#061322; background-image:linear-gradient(120deg,#071321 0%,#0a1f35 52%,#0f3b63 100%); border-bottom:1px solid #1c4d7a; text-align:center; color:#ffffff; }
            .header h1 { margin:0; font-size:24px; font-weight:700; letter-spacing:0.4px; color:#ffffff; }
            .header h2 { margin:6px 0 0; font-size:15px; font-weight:600; color:#6de9ff; letter-spacing:0.2px; }
            .header-icon { font-size:42px; margin-bottom:8px; display:block; color:#6de9ff; }
            .badge { display:inline-block; margin-top:10px; padding:6px 12px; border-radius:999px; background:#0c2138; color:#6de9ff; border:1px solid #24c8f5; font-size:11px; font-weight:700; letter-spacing:0.4px; }
            .content { padding:28px 24px; color:#344a63; font-size:14px; line-height:1.7; }
            .content h1, .content h2, .content h3 { color:#10263f; }
            .content p { margin:0 0 10px 0; color:#344a63; }
            .greeting { font-size:16px; color:#10263f; margin:0 0 12px 0; }
            .message { color:#344a63; font-size:14px; margin-bottom:16px; }
            .amount, .coin-amount { font-size:32px; color:#0f75bc; font-weight:800; text-align:center; margin:18px 0; letter-spacing:0.3px; }
            .coin-icon { color:#0f75bc; }
            .info-box { background:#eaf6ff; border:1px solid #cae8ff; border-left:4px solid #24c8f5; padding:14px 16px; margin:14px 0; border-radius:10px; color:#1f5f92; font-size:13px; }
            .info-box p { margin:6px 0; color:#1f5f92; }
            .info-box strong { color:#10263f; }
            .info-label { font-weight:600; color:#163352; margin-bottom:4px; display:block; font-size:13px; }
            .info-value { color:#344a63; font-size:14px; }
            .detail-card { width:100%; border:1px solid #dbe6f3; border-radius:12px; border-collapse:separate; border-spacing:0; margin-top:14px; background:#ffffff; }
            .detail-card tr + tr td { border-top:1px solid #e8eff8; }
            .detail-card td { padding:12px 14px; font-size:14px; }
            .detail-card .label { color:#617991; width:42%; }
            .detail-card .value { color:#163352; font-weight:700; text-align:right; }
            .btn-container, .cta { text-align:center; margin:18px 0; }
            .button, .btn { display:inline-block; background:#0f75bc; color:#ffffff !important; text-decoration:none; padding:12px 28px; border-radius:10px; font-weight:700; font-size:14px; box-shadow:0 4px 12px rgba(15,117,188,0.25); letter-spacing:0.2px; }
            .button:hover, .btn:hover { background:#0c5e96; }
            .status-badge { background:#0c2138; color:#6de9ff; padding:6px 14px; border-radius:999px; font-weight:700; display:inline-block; border:1px solid #24c8f5; font-size:12px; letter-spacing:0.2px; margin:10px 0; }
            .success-badge { background:#0e3b2e; color:#86efac; padding:6px 14px; border-radius:999px; font-weight:700; display:inline-block; border:1px solid #34d399; font-size:12px; }
            .warning-badge, .priority-badge { background:#3b2a0c; color:#fcd34d; padding:6px 14px; border-radius:999px; font-weight:700; display:inline-block; border:1px solid #f59e0b; font-size:12px; }
            .danger-badge { background:#3b0c0c; color:#fca5a5; padding:6px 14px; border-radius:999px; font-weight:700; display:inline-block; border:1px solid #ef4444; font-size:12px; }
            .bonus-badge { background:#0c2138; color:#6de9ff; padding:4px 10px; border-radius:12px; font-size:13px; font-weight:700; display:inline-block; border:1px solid #24c8f5; margin-left:8px; }
            .success-icon { font-size:54px; text-align:center; margin:18px 0; color:#0f75bc; }
            .gift-card { background:#eaf6ff; border:2px dashed #24c8f5; border-radius:14px; padding:22px; text-align:center; margin:0 auto 24px; color:#1f5f92; }
            .gift-title { color:#0f75bc; font-size:13px; text-transform:uppercase; font-weight:700; letter-spacing:1px; margin-bottom:14px; }
            .gift-items { display:flex; flex-direction:column; gap:10px; align-items:center; }
            .gift-item { font-size:24px; font-weight:800; color:#10263f; }
            .gift-item.cash { color:#0f75bc; }
            .gift-item.coin { color:#0c5e96; }
            .reason-container, .note { background:#eaf6ff; border:1px solid #cae8ff; border-left:4px solid #24c8f5; padding:14px 18px; margin:14px 0; border-radius:10px; color:#1f5f92; font-size:13px; line-height:1.7; }
            .reason-label { font-size:11px; color:#0f75bc; text-transform:uppercase; font-weight:700; display:block; margin-bottom:4px; letter-spacing:0.4px; }
            .reason-text { color:#10263f; font-style:italic; font-weight:500; }
            .footer { background:#fbfdff; padding:18px 20px; text-align:center; border-top:1px solid #e6eef8; color:#6c8098; font-size:12px; }
            .footer p { margin:4px 0; font-size:12px; color:#6c8098; }
            .social-links { margin-top:10px; }
            .social-link { color:#0f75bc; text-decoration:none; margin:0 8px; font-size:12px; }
            hr { border:none; border-top:1px solid #e6eef8; margin:14px 0; }
            code { background:#0c2138; color:#6de9ff; padding:2px 6px; border-radius:6px; font-family:'Monaco','Consolas',monospace; font-size:12px; }
            a { color:#0f75bc; }
            .plan-name { font-size:20px; margin-top:8px; color:#6de9ff; font-weight:600; letter-spacing:0.3px; }
            .price { font-size:34px; color:#0f75bc; font-weight:800; text-align:center; margin:18px 0; letter-spacing:0.3px; }
            .info-row { display:flex; justify-content:space-between; align-items:center; margin:8px 0; gap:10px; flex-wrap:wrap; }
            .info-row .info-label { font-weight:600; color:#163352; margin-bottom:0; }
            .info-row .info-value { color:#344a63; }
            .discount-badge { background:#0c2138; color:#6de9ff; padding:8px 16px; border-radius:999px; display:inline-block; margin:14px 0; font-weight:700; border:1px solid #24c8f5; font-size:13px; }
            .features-box { background:#0c2138; background-image:linear-gradient(120deg,#071321 0%,#0a1f35 60%,#0f3b63 100%); color:#e0f2ff; padding:22px 24px; border-radius:14px; margin:22px 0; border:1px solid #1c4d7a; }
            .features-box h3 { margin:0 0 12px 0; font-size:18px; color:#6de9ff; }
            .features-list { list-style:none; padding:0; margin:8px 0 0 0; }
            .features-list li { padding:6px 0 6px 26px; position:relative; color:#cfe5f7; font-size:14px; line-height:1.55; }
            .features-list li::before { content:"\\2713"; position:absolute; left:0; top:6px; color:#6de9ff; font-weight:700; }
            .fail-icon, .warning-icon { font-size:54px; text-align:center; margin:18px 0; }
            .otp-box { background:#0c2138; background-image:linear-gradient(120deg,#071321 0%,#0a1f35 60%,#0f3b63 100%); border:1px solid #24c8f5; color:#6de9ff; font-size:34px; font-weight:800; letter-spacing:8px; text-align:center; padding:18px 16px; border-radius:14px; margin:18px 0; }
            .otp-label { text-align:center; color:#0f75bc; font-size:12px; font-weight:700; text-transform:uppercase; letter-spacing:1px; margin-top:6px; }
            .countdown { text-align:center; color:#1f5f92; font-size:13px; margin:8px 0 16px; }
            .warning-banner { background:#3b2a0c; border:1px solid #f59e0b; color:#fcd34d; padding:12px 16px; border-radius:10px; font-size:13px; margin:14px 0; }
            .severity-low { color:#6de9ff; }
            .severity-medium { color:#fcd34d; }
            .severity-high { color:#fca5a5; }
            """;
}
