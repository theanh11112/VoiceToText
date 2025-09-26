import { NativeModules, NativeEventEmitter } from 'react-native';

const { SpeechModule } = NativeModules;
const speechEmitter = new NativeEventEmitter(SpeechModule);

export default {
  startListening: () => SpeechModule.startListening(),
  stopListening: () => SpeechModule.stopListening(),

  addSpeechResultListener: (callback) =>
    speechEmitter.addListener('onSpeechResult', callback),

  addSpeechPartialListener: (callback) =>
    speechEmitter.addListener('onSpeechPartial', callback),

  addSpeechErrorListener: (callback) =>
    speechEmitter.addListener('onSpeechError', callback),

  addSpeechStartedListener: (callback) =>
    speechEmitter.addListener('onSpeechStarted', callback),

  addSpeechStoppedListener: (callback) =>
    speechEmitter.addListener('onSpeechStopped', callback),
};
