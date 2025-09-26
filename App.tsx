import React, { useEffect, useState } from 'react';
import { View, Text, Button, PermissionsAndroid, StyleSheet, NativeModules, NativeEventEmitter } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import SpeechModule from './SpeechModule';

type SpeechEvent = { text?: string };
type SmsEvent = { from?: string; body?: string };

export default function App() {
  const [speechText, setSpeechText] = useState('');
  const [smsList, setSmsList] = useState<SmsEvent[]>([]);

  useEffect(() => {
    console.log("📱 App.tsx mounted, bắt đầu đăng ký listener SMS...");

    // Yêu cầu quyền
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

    // Lấy SMS cuối cùng khi app load
    const fetchSms = async () => {
      try {
        if (NativeModules.SmsModule) {
          const lastSms = await NativeModules.SmsModule.getLastSms();
          if (lastSms) setSmsList([lastSms]);
        }
      } catch (e) {
        console.error('Lỗi khi lấy SMS:', e);
      }
    };
    fetchSms();

    // Tạo emitter cho SMS realtime
    const smsEmitter = new NativeEventEmitter(NativeModules.SmsModule);
    const smsSub = smsEmitter.addListener('onSmsReceived', (data: SmsEvent) => {
      console.log("📩 onSmsReceived event:", data);
      setSmsList(prev => [...prev, { from: data.from || '', body: data.body || '' }]);
    });

    // Lắng nghe kết quả giọng nói
    const speechSub = SpeechModule.addSpeechResultListener((e: SpeechEvent) => {
      setSpeechText(e.text || '');
    });

    return () => {
      smsSub.remove();
      speechSub.remove();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.block}>
        <Button title="Start Voice Recognition" onPress={() => SpeechModule.startListening()} />
        <View style={styles.spacer} />
        <Button title="Stop Listening" onPress={() => SpeechModule.stopListening()} />
      </View>

      <View style={styles.block}>
        <Text style={styles.label}>Voice Result:</Text>
        <Text>{speechText}</Text>
      </View>

      <View style={styles.block}>
        <Text style={styles.label}>Latest SMS:</Text>
        {smsList.map((s, idx) => (
          // eslint-disable-next-line react-native/no-inline-styles
          <View key={idx} style={{ marginBottom: 4 }}>
            <Text>From: {s.from}</Text>
            <Text>Body: {s.body}</Text>
          </View>
        ))}
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
