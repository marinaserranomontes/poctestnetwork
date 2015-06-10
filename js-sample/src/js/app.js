var API_KEY,
    SESSION_ID,
    TOKEN,
    TEST_TIMEOUT_MS = 15000, // 15 seconds
    currentScript = document.currentScript;

var compositeOfCallbacks = require('./lib/composite-callbacks.js'),
    performQualityTest = require('./lib/quality-test.js');

var publisherEl = document.createElement('div'),
    subscriberEl = document.createElement('div'),
    session,
    publisher,
    statusContainerEl,
    statusMessageEl,
    statusIconEl;

var callbacks = {
  onInitPublisher: function onInitPublisher(error) {
    if (error) {
      statusMessageEl.innerText = 'Could not acquire your camera';
      return;
    }
    statusMessageEl.innerText = 'Connecting to session';
  },
  onPublish: function onPublish(error) {
    if (error) {
      // handle publishing errors here
      statusMessageEl.innerText = 'Could not publish video';
      return;
    }
    statusMessageEl.innerText = 'Subscribing to video';
    session.subscribe(
      publisher.stream,
      subscriberEl,
      {
        audioVolume: 0,
        testNetwork: true
      },
      callbacks.onSubscribe
    );
  },
  onSubscribe: function onSubscribe(error, subscribe) {
    if (error) {
      statusMessageEl.innerText = 'Could not subscribe to video';
      return;
    }

    statusMessageEl.innerText = 'Checking your available bandwidth';

    performQualityTest({subscriber: subscribe, timeout: TEST_TIMEOUT_MS}, function(error, results) {
      // here we can decide what to do.
      console.log('Test concluded', results);

      if (results.video.averageBytes > 150000 && results.video.averagePacketLoss < 0.03 && results.audio.averagePacketLoss < 0.03) {
        statusMessageEl.innerText = 'You\'re all set!';
        statusIconEl.src = 'assets/icon_tick.svg';
      } else if (results.video.averageBytes > 30000) {
        statusMessageEl.innerText = 'Your bandwidth is too low for video';
        statusIconEl.src = 'assets/icon_warning.svg';
      } else {
        statusMessageEl.innerText = 'You can\'t successfully connect';
        statusIconEl.src = 'assets/icon_error.svg';
      }
    });
  },
  onConnect: function onConnect(error) {
    if (error) {
      statusMessageEl.innerText = 'Could not connect to OpenTok';
    }
  }
};

compositeOfCallbacks(
  callbacks,
  ['onInitPublisher', 'onConnect'],
  function(error) {
    statusMessageEl.innerText = 'Publishing video';
    if (error) {
      // handle getUserMedia + session connect errors here
      // statusMessageEl.innerText = 'Could not publish video';
      return;
    }
    session.publish(publisher, callbacks.onPublish);
  }
);

publisher = OT.initPublisher(publisherEl, {
  resolution: '1280x720'
}, callbacks.onInitPublisher);

document.addEventListener('DOMContentLoaded', function() {
  API_KEY = currentScript.attributes.api_key.nodeValue;
  SESSION_ID = currentScript.attributes.session_id.nodeValue;
  TOKEN = currentScript.attributes.token.nodeValue;

  session = OT.initSession(API_KEY, SESSION_ID);
  session.connect(TOKEN, callbacks.onConnect);
  statusContainerEl = document.getElementById('status_container');
  statusMessageEl = statusContainerEl.querySelector('p');
  statusIconEl = statusContainerEl.querySelector('img');
});