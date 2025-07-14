import express from 'express';
import bodyParser from 'body-parser';
import { IgApiClient, IgLoginTwoFactorRequiredError, IgCheckpointError } from 'instagram-private-api';

const app = express();
app.use(express.static('public'));
app.use(bodyParser.json());

// store temporary IgApiClient sessions for 2FA/checkpoint handling
const igSessions = new Map();

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

  let ig = igSessions.get(username);
  if (!ig) {
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
      igSessions.set(username, ig);
      return res.status(401).json({
        twoFactorRequired: true,
        twoFactorIdentifier: e.response.body.two_factor_info.two_factor_identifier,
      });
    }
    if (e instanceof IgCheckpointError) {
      igSessions.set(username, ig);
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
