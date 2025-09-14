# Email Setup Instructions for SkillVerse

## Gmail Configuration for skillverseexe@gmail.com

### Step 1: Enable 2-Factor Authentication
1. Go to your Google Account settings: https://myaccount.google.com/
2. Click on "Security" in the left sidebar
3. Under "Signing in to Google", click on "2-Step Verification"
4. Follow the steps to enable 2-Step Verification if not already enabled

### Step 2: Generate App Password
1. In the same "Security" section, click on "App passwords"
2. Select "Mail" as the app and "Other (Custom name)" as the device
3. Enter "SkillVerse Backend" as the custom name
4. Click "Generate"
5. Copy the 16-character app password that appears

### Step 3: Update application.yml
Replace `your-app-password-here` in `src/main/resources/application.yml` with the generated app password:

```yaml
spring:
  mail:
    username: skillverseexe@gmail.com
    password: your-16-character-app-password-here
```

### Step 4: Test Email Sending
1. Restart the application
2. Try registering a new user
3. Check if the OTP email arrives in the user's inbox

## Important Notes:
- Never commit the real app password to version control
- The app password is different from your regular Gmail password
- App passwords are only available when 2-Step Verification is enabled
- If emails still don't work, check Gmail's "Less secure app access" settings (though App Passwords are preferred)

## Fallback Mode:
If email sending fails, the service will automatically fall back to console logging mode, so you can still see the OTP in the application logs for testing.

## Alternative Email Services:
If Gmail doesn't work, you can also configure:
- SendGrid
- AWS SES
- Mailgun
- Other SMTP providers

Just update the SMTP settings in application.yml accordingly.