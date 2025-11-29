package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.SttResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface AiSpeechService {

    SttResponse transcribeAudio(MultipartFile audio, String language);

    TtsResult synthesizeSpeech(String text, String voice, Double speed);

    class TtsResult {
        private final byte[] audio;
        private final MediaType contentType;

        public TtsResult(byte[] audio, MediaType contentType) {
            this.audio = audio;
            this.contentType = contentType;
        }

        public byte[] getAudio() { return audio; }
        public MediaType getContentType() { return contentType; }
    }
}

