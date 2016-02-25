package com.ibm.watson.developer_cloud.android.speech_to_text.v1;

class TranscribedElement {
  public String _transcription;
  public double _startTime;
  public double _endTime;

  public TranscribedElement(String t, double s, double f) {
    _transcription = t;
    _startTime = s;
    _endTime = f;
  }
}