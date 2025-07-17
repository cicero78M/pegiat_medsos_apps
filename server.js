import express from 'express';
import bodyParser from 'body-parser';
import { IgApiClient, IgLoginTwoFactorRequiredError, IgCheckpointError } from 'instagram-private-api';

const app = express();
app.use(express.static('public'));
app.use(bodyParser.json());

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

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log('Server running on port', port);
});
