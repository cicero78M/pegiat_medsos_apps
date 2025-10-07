import express from 'express';
import { IgApiClient, IgLoginTwoFactorRequiredError, IgCheckpointError } from 'instagram-private-api';

const app = express();
app.use(express.static('public'));
app.use(express.json());

const linkFields = ['instagram_link', 'facebook_link', 'twitter_link', 'tiktok_link', 'youtube_link'];
const linkReportsStore = {
  regular: [],
  special: [],
};

const normalizeLink = (link) => (link || '').trim().toLowerCase();

const toArray = (value) => {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
};

const parseRequestedLinks = (query) => {
  const rawLinks = [
    ...toArray(query.links),
    ...toArray(query['links[]']),
    ...toArray(query.link),
  ];
  return rawLinks
    .map((link) => (typeof link === 'string' ? link : String(link || '')))
    .map((link) => link.trim())
    .filter((link) => link.length > 0);
};

const collectDuplicates = (reports, requestedLinks) => {
  const requested = new Set(requestedLinks.map((link) => normalizeLink(link)).filter((link) => link));
  if (!requested.size) return [];
  const duplicates = new Set();
  for (const report of reports) {
    for (const field of linkFields) {
      const normalized = normalizeLink(report[field]);
      if (normalized && requested.has(normalized)) {
        duplicates.add(normalized);
      }
    }
  }
  return Array.from(duplicates);
};

const getCollection = (isSpecial) => (isSpecial ? linkReportsStore.special : linkReportsStore.regular);

const handleGetLinkReports = (isSpecial) => (req, res) => {
  const collection = getCollection(isSpecial);
  const requestedLinks = parseRequestedLinks(req.query);
  if (requestedLinks.length) {
    const duplicates = collectDuplicates(collection, requestedLinks);
    return res.json({ data: collection, duplicates });
  }
  res.json({ data: collection });
};

const handlePostLinkReports = (isSpecial) => (req, res) => {
  const collection = getCollection(isSpecial);
  const payload = req.body || {};
  const record = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    shortcode: payload.shortcode || '',
    user_id: payload.user_id || '',
  };
  for (const field of linkFields) {
    record[field] = payload[field] || '';
  }
  record.created_at = new Date().toISOString();
  collection.push(record);
  res.status(201).json({ data: record });
};

// store temporary IgApiClient sessions for 2FA/checkpoint handling
// each entry holds an object { client: IgApiClient, timestamp: number }
const igSessions = new Map();
// remove sessions older than 10 minutes
const SESSION_EXPIRY_MS = 10 * 60 * 1000;
setInterval(() => {
  const now = Date.now();
  for (const [username, session] of igSessions.entries()) {
    if (now - session.timestamp > SESSION_EXPIRY_MS) {
      igSessions.delete(username);
    }
  }
}, 60 * 1000);

app.get('/', (req, res) => {
  res.sendFile(`${process.cwd()}/public/index.html`);
});

app.get('/autopost', (req, res) => {
  res.sendFile(`${process.cwd()}/public/autopost.html`);
});

app.get('/api/link-reports', handleGetLinkReports(false));
app.post('/api/link-reports', handlePostLinkReports(false));
app.get('/api/link-reports-khusus', handleGetLinkReports(true));
app.post('/api/link-reports-khusus', handlePostLinkReports(true));

app.post('/login', async (req, res) => {
  const { username, password, twoFactorCode, twoFactorIdentifier, checkpointCode } = req.body;
  if (!username) {
    return res.status(400).json({ error: 'Missing username' });
  }

  const sessionEntry = igSessions.get(username);
  let ig;
  if (sessionEntry && Date.now() - sessionEntry.timestamp < SESSION_EXPIRY_MS) {
    ig = sessionEntry.client;
  } else {
    if (sessionEntry) igSessions.delete(username);
    ig = new IgApiClient();
    ig.state.generateDevice(username);
  }

  try {
    await ig.simulate.preLoginFlow();
    let user;
    if (twoFactorCode && twoFactorIdentifier) {
      user = await ig.account.twoFactorLogin({
        username,
        verificationCode: twoFactorCode,
        twoFactorIdentifier,
      });
    } else if (checkpointCode) {
      await ig.challenge.sendSecurityCode(checkpointCode);
      user = await ig.account.currentUser();
    } else {
      if (!password) return res.status(400).json({ error: 'Missing password' });
      user = await ig.account.login(username, password);
    }
    await ig.simulate.postLoginFlow();
    const info = await ig.account.info(user.pk);
    igSessions.delete(username);
    res.json({
      user: {
        username: info.username,
        fullName: info.full_name,
        followerCount: info.follower_count,
        profilePic: info.profile_pic_url,
      },
    });
  } catch (e) {
    if (e instanceof IgLoginTwoFactorRequiredError) {
      igSessions.set(username, { client: ig, timestamp: Date.now() });
      return res.status(401).json({
        twoFactorRequired: true,
        twoFactorIdentifier: e.response.body.two_factor_info.two_factor_identifier,
      });
    }
    if (e instanceof IgCheckpointError) {
      igSessions.set(username, { client: ig, timestamp: Date.now() });
      await ig.challenge.auto(true);
      return res.status(401).json({ checkpoint: true });
    }
    console.error(e);
    igSessions.delete(username);
    res.status(400).json({ error: 'Login failed' });
  }
});

app.get('/tiktok/:username', async (req, res) => {
  const { username } = req.params;
  if (!username) return res.status(400).json({ error: 'Missing username' });
  try {
    const resp = await fetch(`https://tikwm.com/api/user/info?unique_id=${encodeURIComponent(username)}`);
    const data = await resp.json();
    if (data.code !== 0) return res.status(400).json({ error: 'User not found' });
    const user = data.data.user;
    res.json({
      user: {
        username: user.uniqueId,
        nickname: user.nickname,
        avatar: user.avatarLarger,
      },
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Failed to fetch profile' });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log('Server running on port', port);
});
