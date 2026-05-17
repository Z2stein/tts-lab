package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import java.util.List;

public interface SpeakerVoiceAnalysisUpdateService {
    List<SpeakerCharacter> syncProjectCharacters(String projectId, List<SpeakerVoiceAnalysisItem> speakers);
}
