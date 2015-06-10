var sum = require('lodash.sum'),
    pluck = require('lodash.pluck');

module.exports = function(statsBuffer, seconds) {
  var stats = {};
  ['video', 'audio'].forEach(function(type) {
    stats[type] = {
      averagePackets: sum(pluck(statsBuffer, type), 'packetsReceived') / seconds,
      averageBytes: sum(pluck(statsBuffer, type), 'bytesReceived') / seconds,
      averagePacketsLost: sum(pluck(statsBuffer, type), 'packetsLost') / seconds
    };
    stats[type].averagePacketLoss = stats[type].averagePacketsLost / stats[type].averagePackets;
  });
  stats.windowSize = seconds;
  return stats;
};