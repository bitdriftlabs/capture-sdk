const path = require('path');
const pak = require('../../dist/react-native/package.json');

module.exports = {
  dependencies: {
    [pak.name]: {
      root: path.join(__dirname, '../../dist/react-native'),
    },
  },
};

