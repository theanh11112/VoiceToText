import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  Button,
  PermissionsAndroid,
  StyleSheet,
  NativeModules,
  NativeEventEmitter,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import SpeechModule from './SpeechModule';
import Tts from 'react-native-tts';

type SpeechEvent = { text?: string };
type SmsEvent = { from?: string; body?: string; translated?: string };

export default function App() {
  const [speechText, setSpeechText] = useState('');
  const [smsList, setSmsList] = useState<SmsEvent[]>([]);

  useEffect(() => {
    console.log('📱 App mounted...');

    async function requestPermissions() {
      try {
        await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
          PermissionsAndroid.PERMISSIONS.READ_SMS,
          PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
        ]);
      } catch (err) {
        console.warn(err);
      }
    }
    requestPermissions();

    const smsEmitter = new NativeEventEmitter(NativeModules.SmsModule);
    const smsSub = smsEmitter.addListener('onSmsReceived', async (data: SmsEvent) => {
      const translated = await translateText(data.body || '');
      setSmsList((prev) => [{ ...data, translated }, ...prev]);
    });

    // Set listener ready để flush SMS cũ
    if (NativeModules.SmsModule?.flushCachedSmsToJSForJS) {
      NativeModules.SmsModule.flushCachedSmsToJSForJS();
    }

    // Voice-to-text listener
    const speechSub = SpeechModule.addSpeechResultListener((e: SpeechEvent) => {
      setSpeechText(e.text || '');
    });

    Tts.setDefaultLanguage('vi-VN');
    Tts.setDefaultRate(0.5);

    return () => {
      smsSub.remove();
      speechSub.remove();
      Tts.stop();
      if ((Tts as any).shutdown) (Tts as any).shutdown();
    };
  }, []);

  const translateText = async (text: string) => {
    if (!text) return '';
    try {
      const res = await fetch('https://libretranslate.com/translate', {
        method: 'POST',
        body: JSON.stringify({ q: text, source: 'vi', target: 'en', format: 'text' }),
        headers: { 'Content-Type': 'application/json' },
      });
      const data = await res.json();
      return data.translatedText;
    } catch (e) {
      console.error(e);
      return '';
    }
  };

  const readLatestSms = () => {
    if (smsList.length > 0) {
      const latest = smsList[0];
      const textToRead = `Tin nhắn từ ${latest.from}. Nội dung: ${latest.body}`;
      Tts.speak(textToRead);
    } else {
      Tts.speak('Chưa có tin nhắn nào.');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.block}>
        <Button title="🎤 Start Voice" onPress={() => SpeechModule.startListening()} />
        <View style={styles.spacer} />
        <Button title="⏹ Stop Voice" onPress={() => SpeechModule.stopListening()} />
      </View>

      <View style={styles.block}>
        <Text style={styles.label}>Voice Result:</Text>
        <Text>{speechText}</Text>
      </View>

      <View style={styles.block}>
        <Text style={styles.label}>Latest SMS:</Text>
        {smsList.map((s, idx) => (
          <View key={idx} style={{ marginBottom: 8 }}>
            <Text>📩 From: {s.from}</Text>
            <Text>🇻🇳 Body: {s.body}</Text>
            {s.translated ? <Text>🇬🇧 Translated: {s.translated}</Text> : null}
          </View>
        ))}
        <View style={styles.spacer} />
        <Button title="📢 Read Aloud" onPress={readLatestSms} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  block: { marginVertical: 12 },
  label: { fontWeight: 'bold' },
  spacer: { height: 8 },
});
