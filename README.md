# Telegram Expense Bot

Personal monthly expense tracker built with:

- Java 21
- Spring Boot 4.1
- Telegram Bot API (webhook)
- PostgreSQL
- Docker
- Render

The application uses a **Telegram webhook**, which is the better fit for a free Render Web Service because Telegram sends inbound HTTP requests to the service.

## Features

Commands:

```text
/start
/help

/add 5 coffee
/add 7.50 lunch
/add 20000 khr breakfast
/add 100000 khr fuel

/today
/month
/history

/budget 500
/budget 2000000 khr

/delete 15
/clear
/whoami
```

You can also omit `/add`:

```text
5 coffee
25000 khr lunch
```

Default currency is USD.

After adding an expense, the bot shows category buttons in Telegram. Tap a button to update the saved category for that expense.

The bot automatically categorizes common expenses:

- COFFEE
- FOOD
- CAR
- TRANSPORT
- SHOPPING
- BILL
- ENTERTAINMENT
- HEALTH
- OTHER

## 1. Create a Telegram bot

Open Telegram and message `@BotFather`.

Run:

```text
/newbot
```

BotFather gives you a token. Save it as:

```text
TELEGRAM_BOT_TOKEN
```

Never commit the real token to Git.

## 2. Create PostgreSQL

For a long-lived free hobby database, you can use Neon PostgreSQL.

Create a database and copy:

- Host
- Database
- Username
- Password

Create the JDBC URL:

```text
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

Example:

```text
jdbc:postgresql://ep-example-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

Use the pooler hostname when Neon provides one.

## 3. Run locally

Set environment variables.

### Windows PowerShell

```powershell
$env:TELEGRAM_BOT_TOKEN="your-token"
$env:TELEGRAM_WEBHOOK_SECRET="your-secret"
$env:APP_BASE_URL="https://your-public-url.example"
$env:APP_KHR_TO_USD_RATE="4100"
$env:DB_URL="jdbc:postgresql://..."
$env:DB_USERNAME="..."
$env:DB_PASSWORD="..."
mvn spring-boot:run
```

A Telegram webhook needs a public HTTPS URL. For purely local development, use a tunnel such as Cloudflare Tunnel/ngrok, or deploy to Render first.

## 4. Push to GitHub

```bash
git init
git add .
git commit -m "Initial Telegram expense bot"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/telegram-expense-bot.git
git push -u origin main
```

## 5. Deploy to Render

1. Open Render.
2. New -> Blueprint, then choose your GitHub repository containing `render.yaml`.
   - Or create a Web Service manually and select Docker.
3. Choose the Free web-service instance if you want the free tier.
4. Add these secret environment variables:

```text
TELEGRAM_BOT_TOKEN
TELEGRAM_WEBHOOK_SECRET
DB_URL
DB_USERNAME
DB_PASSWORD
```

Optional:

```text
ALLOWED_TELEGRAM_USER_ID
APP_KHR_TO_USD_RATE
```

On Render, `APP_BASE_URL` is optional because the app automatically uses Render's `RENDER_EXTERNAL_URL`. If you set `APP_BASE_URL` yourself, do not add a trailing slash.

The application automatically calls Telegram `setWebhook` after startup.

## 6. Check deployment

Open:

```text
https://YOUR-RENDER-SERVICE.onrender.com/health
```

Expected response:

```json
{
  "status": "UP"
}
```

Then send this to your Telegram bot:

```text
/whoami
```

The bot returns your Telegram user ID.

If this bot is only for you, add that ID to Render:

```text
ALLOWED_TELEGRAM_USER_ID=123456789
```

For multiple allowed users, separate IDs with commas:

```text
ALLOWED_TELEGRAM_USER_ID=123456789,987654321
```

Redeploy.

## Example usage

```text
/add 5 coffee
```

Response:

```text
✅ Expense added
#12
☕ COFFEE
coffee
$5.00 USD
```

```text
/month
```

Response:

```text
📊 August 2026

USD
☕ COFFEE: $100.00
🚗 CAR: $80.00
🧾 BILL: $20.00

💰 Total USD: $200.00
🎯 Budget: $500.00
✅ Remaining: $300.00
```

For KHR:

```text
/add 25000 khr lunch
```

and:

```text
/budget 2000000 khr
```

The bot keeps USD and KHR sections separate, then adds a converted grand total when a month has more than one currency.
The conversion uses `APP_KHR_TO_USD_RATE`, which defaults to `4100`.
`/today` also shows monthly budget status when a budget is set.

Example mixed-currency total:

```text
Grand total: $206.10 (1 USD = 4100 KHR)
```

## Render free-tier note

Render Free Web Services can sleep after being idle. A Telegram webhook is used so incoming Telegram requests are HTTP traffic that can wake the service. The first message after a long idle period can be slower while Render starts the service.

Do not use a local H2/SQLite database on Render Free for this project because the service filesystem is ephemeral.

## Database schema

Hibernate creates/updates the tables automatically because:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

For a larger production app, switch to Flyway/Liquibase migrations.
