package com.sasami.cinek.experimental;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

public class MainActivity extends Activity {
    private static final int OPEN_VIDEO = 1;
    private VideoView videoView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int space = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(space, space, space, space);

        TextView title = new TextView(this);
        title.setText("CineK Experimental");
        title.setTextSize(24);
        layout.addView(title);

        TextView description = new TextView(this);
        description.setText("Open a video on this device or play a direct HTTPS media URL.");
        layout.addView(description);

        Button openFile = new Button(this);
        openFile.setText("Open video file");
        openFile.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent, OPEN_VIDEO);
        });
        layout.addView(openFile);

        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://example.com/video.mp4");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(url);

        Button playUrl = new Button(this);
        playUrl.setText("Play URL");
        playUrl.setOnClickListener(view -> {
            Uri media = Uri.parse(url.getText().toString().trim());
            if (!"https".equalsIgnoreCase(media.getScheme()) || media.getHost() == null) {
                status.setText("Enter a direct HTTPS media URL.");
                return;
            }
            play(media);
        });
        layout.addView(playUrl);

        status = new TextView(this);
        status.setText("Choose a video to begin.");
        layout.addView(status);

        videoView = new VideoView(this);
        videoView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        layout.addView(videoView);
        setContentView(layout);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoView);
        videoView.setMediaController(controls);
        videoView.setOnPreparedListener(player -> status.setText("Playing"));
        videoView.setOnErrorListener((player, what, extra) -> {
            status.setText("This video could not be played on this device.");
            return true;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_VIDEO && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            play(data.getData());
        }
    }

    private void play(Uri media) {
        status.setText("Opening video...");
        videoView.setVideoURI(media);
        videoView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) videoView.pause();
    }
}
