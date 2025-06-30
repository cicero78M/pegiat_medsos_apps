import express from 'express';
import bodyParser from 'body-parser';
import puppeteer from 'puppeteer';
import cheerio from 'cheerio';
import fs from 'fs';

const app = express();
app.use(express.static('public'));
app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

app.get('/', (req, res) => {
  res.sendFile(`${process.cwd()}/public/index.html`);
});

app.post('/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password required' });
    return;
  }

  let browser;
  try {
    browser = await puppeteer.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage();
    await page.goto('https://www.tiktok.com/login/phone-or-email/email', { waitUntil: 'networkidle2' });

    await page.type('input[name="username"]', username);
    await page.type('input[type="password"]', password);
    await Promise.all([
      page.click('button[type="submit"]'),
      page.waitForNavigation({ waitUntil: 'networkidle2' })
    ]);

    const cookies = await page.cookies();
    fs.writeFileSync('tiktok-session.json', JSON.stringify(cookies, null, 2));

    const profileUrl = `https://www.tiktok.com/@${username}`;
    await page.goto(profileUrl, { waitUntil: 'networkidle2' });
    const html = await page.content();

    const $ = cheerio.load(html);
    const avatar = $('img[data-e2e="user-avatar"], img[data-e2e="cover-avatar"]').attr('src') || '';
    const followerText = $('[data-e2e="followers-count"]').first().text();
    const followingText = $('[data-e2e="following-count"]').first().text();

    const parseCount = (str) => {
      if (!str) return 0;
      const s = str.replace(/,/g, '').toLowerCase();
      const m = s.match(/^([0-9]*\.?[0-9]+)([km]?)$/);
      if (!m) return parseInt(s) || 0;
      let num = parseFloat(m[1]);
      const suf = m[2];
      if (suf === 'k') num *= 1e3;
      else if (suf === 'm') num *= 1e6;
      return Math.round(num);
    };

    const data = {
      user: { uniqueId: username, avatarThumb: avatar },
      stats: {
        followerCount: parseCount(followerText),
        followingCount: parseCount(followingText)
      }
    };

    res.status(200).json({ data, msg: 'Login berhasil' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  } finally {
    if (browser) {
      await browser.close();
    }
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log('Server running on port', port);
});
