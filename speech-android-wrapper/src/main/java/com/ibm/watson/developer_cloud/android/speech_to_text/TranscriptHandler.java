package com.ibm.watson.developer_cloud.android.speech_to_text.v1;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;

import com.unity3d.player.UnityPlayer;

/**
 * TranscriptHandler provides a simpler API for using the Watson Speech-to-Text
 * service, as well as a method for querying the transcript based on time.
 *
 * This class handles some of the boilerplate invovled in configuring the Watson
 * API so an external caller does not have to do it, as well as lets the caller
 * query the current transcription based on time.  The transcription can be
 * paused and resumed without losing any past transcribed data during the
 * lifetime of the object.
 *
 * A TranscriptHandler object is intended to be very easy to create, because
 * the impetus for making it was creating it via a C# client.  We don't use a
 * DI system here to make creating the object simpler for the remote creator.
 */
public class TranscriptHandler implements ISpeechDelegate {

  private long _startTime;
  private ArrayList<TranscribedElement> _transcribedElements;
  private String _errorString;
  private static TranscriptHandler _instance;
  private boolean _isRecognizing;

  public TranscriptHandler() {
    _isRecognizing = false;
    _transcribedElements = new ArrayList<TranscribedElement>();
  }

  /**
   * @return Singleton instance of TranscriptHandler.
   */
  public static TranscriptHandler getInstance() {
    if(_instance == null){
        synchronized(TranscriptHandler.class){
            _instance = new TranscriptHandler();
        }
    }
    return _instance;
  }

  /**
   * Begin transcribing audio.
   */
  public synchronized void start() {
    if (!_isRecognizing) {

      try {
        initializeSpeechToText();
        _transcribedElements = new ArrayList<TranscribedElement>();
        _startTime = System.currentTimeMillis();
        SpeechToText.sharedInstance().recognize();
        _isRecognizing = true;
      }
      catch (Exception e) {
        UnityPlayer.UnitySendMessage("GameObject", "StartRecognizerError", null);
      }
    }
  }

  /**
   * Halt transcription; data already transcribed is still retained.
   */
  public synchronized void close() {
    if (_isRecognizing) {
      _isRecognizing = false;
      SpeechToText.sharedInstance().stopRecognition();   
    }
  }

  /**
   * Resume transcription, typically after a call to close() or the connection
   * was closed for some reason.
   */
  public synchronized void resume() {
    if (!_isRecognizing) {
      _startTime = System.currentTimeMillis();
      SpeechToText.sharedInstance().recognize();
      _isRecognizing = true;   
    }
  }

  /**
   * Retrieve the portion of the transcription as a String that falls between
   * the given timespan.
   * 
   * @param Start time of transcrtion to fetch, in milliseconds from epoch
   * @param End time of transcrtion to fetch, in milliseconds from epoch
   * @return Raw transcription from the timespan
   */
  public synchronized String getTranscript(long startTime, long endTime) {
    String portion = "";

    for (int i = 0; i<_transcribedElements.size(); i++) {
      TranscribedElement te = _transcribedElements.get(i);

      if (te._startTime >= startTime && te._endTime <= endTime) {
        portion += te._transcription.replace("%HESITATION", "[UH]") + " ";
      }
    }

    return portion;
  }

  /**
   * Handle receipt of a decoded piece of audio from the speech-to-text API
   * 
   * @param Transcribed audio
   */
  @Override
  public synchronized void onMessage(String message) {

    try {
        JSONObject mainObject = new JSONObject(message);
        JSONArray results = mainObject.getJSONArray("results");
        JSONObject result = results.getJSONObject(0);
        JSONArray alternatives = result.getJSONArray("alternatives");

        if (result.getBoolean("final") == true) {
            String val = alternatives.getJSONObject(0).getString("transcript");
            JSONArray timestamps = alternatives.getJSONObject(0).getJSONArray("timestamps");

            for (int i = 0; i < timestamps.length(); i++) {
              JSONArray timestampedString = timestamps.getJSONArray(i);
              String text = timestampedString.getString(0);
              double start = timestampedString.getDouble(1);
              double end = timestampedString.getDouble(2);
              _transcribedElements.add(new TranscribedElement(text, _startTime + (start * 1000.0), _startTime + (end * 1000.0) ));
            }
        }
    }
    catch (JSONException e) {
      return;
    }
  }

  public synchronized String getErrorString() {
    return _errorString;
  }

  private void initializeSpeechToText() {
    String username = "f729fc32-7935-46c7-b7e7-fad90abfb7ff";
    String password = "t0MGLeDHLJtw";
    String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";
    SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), null, new SpeechConfiguration());
    SpeechToText.sharedInstance().setCredentials(username, password);
    SpeechToText.sharedInstance().setModel("en-US_BroadbandModel");
    SpeechToText.sharedInstance().setDelegate(this);
  }

  private URI getHost(String url){
      try {
          return new URI(url);
      } catch (URISyntaxException e) {
          e.printStackTrace();
      }
      return null;
  }

  public synchronized void onClose(int code, String reason, boolean remote) {
    _isRecognizing = false;
    // TODO: does the audio capture thread get closed in this instance?
    // probably not, callback is from the websocketuploader.
    // we can just stop the recognition, though.
    // stopRecognition()
    // TODO: attempt reconnect at a reasonable interval
  }

  public void onOpen() {
  }

  public void onError(String error) {
    //stopRecognition()
  }

  public void onAmplitude(double amplitude, double volume) {
    Double d = new Double(volume);
    UnityPlayer.UnitySendMessage("GameObject", "Volume", Integer.toString(d.intValue()));
  }
}
