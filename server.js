import express from 'express';
import bodyParser from 'body-parser';
import { IgApiClient } from 'instagram-private-api';

const app = express();
app.use(express.static('public'));
app.use(bodyParser.json());

app.get('/', (req, res) => {
  res.sendFile(`${process.cwd()}/public/index.html`);
});

app.get('/autopost', (req, res) => {
  res.sendFile(`${process.cwd()}/public/autopost.html`);
});

app.post('/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Missing credentials' });
  }
  const ig = new IgApiClient();
  ig.state.generateDevice(username);
  try {
    await ig.simulate.preLoginFlow();
    const user = await ig.account.login(username, password);
    await ig.simulate.postLoginFlow();
    const info = await ig.account.info(user.pk);
    res.json({
      user: {
        username: info.username,
        fullName: info.full_name,
        followerCount: info.follower_count,
      },
    });
  } catch (e) {
    console.error(e);
    res.status(400).json({ error: 'Login failed' });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log('Server running on port', port);
});
