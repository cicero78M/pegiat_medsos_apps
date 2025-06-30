import express from 'express';
import bodyParser from 'body-parser';

const app = express();
app.use(express.static('public'));
app.use(bodyParser.json());

app.get('/', (req, res) => {
  res.sendFile(`${process.cwd()}/public/index.html`);
});

app.post('/login', (req, res) => {
  res.status(501).json({ error: 'Use QR code login via the Android app' });
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log('Server running on port', port);
});
