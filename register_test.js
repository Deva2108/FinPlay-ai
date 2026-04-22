const axios = require('axios');
axios.post('http://localhost:8080/user/register', {
  name: 'Test Test',
  email: 'test111@example.com',
  password: 'Password123'
}).then(console.log).catch(console.error);
