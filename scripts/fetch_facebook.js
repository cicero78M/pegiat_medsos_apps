import { parse } from 'node-html-parser';

const DEFAULT_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
  'Accept-Language': 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7',
  'Accept-Encoding': 'gzip, deflate',
  'Connection': 'keep-alive'
};

function parseCookies(setCookieHeaders = []) {
  const jar = {};
  for (const entry of setCookieHeaders) {
    const [cookie] = entry.split(';');
    const [name, value] = cookie.split('=');
    jar[name.trim()] = value.trim();
  }
  return jar;
}

function cookieHeader(jar) {
  return Object.entries(jar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

async function fetchLoginPage() {
  const res = await fetch('https://m.facebook.com/login.php', {
    headers: DEFAULT_HEADERS
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch login page: ${res.status}`);
  }
  const cookies = parseCookies(res.headers.raw()['set-cookie']);
  const html = await res.text();
  const root = parse(html);
  const lsd = root.querySelector('input[name="lsd"]')?.getAttribute('value');
  const jazoest = root.querySelector('input[name="jazoest"]')?.getAttribute('value');
  return { cookies, lsd, jazoest };
}

async function postLogin(email, pass, tokens) {
  const form = new URLSearchParams();
  form.set('lsd', tokens.lsd || '');
  form.set('jazoest', tokens.jazoest || '');
  form.set('email', email);
  form.set('pass', pass);

  const res = await fetch('https://m.facebook.com/login/device-based/regular/login/?login_attempt=1', {
    method: 'POST',
    headers: {
      ...DEFAULT_HEADERS,
      'Content-Type': 'application/x-www-form-urlencoded',
      'Cookie': cookieHeader(tokens.cookies),
      'Referer': 'https://m.facebook.com/login.php'
    },
    body: form.toString()
  });
  if (!res.ok) {
    throw new Error(`Login failed: ${res.status}`);
  }
  const cookies = { ...tokens.cookies, ...parseCookies(res.headers.raw()['set-cookie']) };
  const html = await res.text();
  return { cookies, html };
}

async function fetchPage(url, cookies = {}) {
  const res = await fetch(url, {
    headers: {
      ...DEFAULT_HEADERS,
      'Cookie': cookieHeader(cookies)
    }
  });
  if (!res.ok) {
    throw new Error(`Request failed with status ${res.status}`);
  }
  return await res.text();
}

async function main() {
  const url = process.argv[2] || 'https://m.facebook.com/login.php';
  const email = process.env.FB_EMAIL;
  const password = process.env.FB_PASS;

  try {
    if (email && password) {
      const tokens = await fetchLoginPage();
      const { cookies } = await postLogin(email, password, tokens);
      const html = await fetchPage(url, cookies);
      console.log(html);
    } else {
      const html = await fetchPage(url);
      console.log(html);
    }
  } catch (err) {
    console.error('Error fetching page:', err.message);
    process.exit(1);
  }
}

main();
