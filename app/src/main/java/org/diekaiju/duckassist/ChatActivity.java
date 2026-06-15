package org.diekaiju.duckassist;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

public class ChatActivity extends MainActivity {

    private boolean isDismissing = false;
    private int cardHeight = 0;
    private int collapsedHeight = 0;

    private boolean shouldUseDrawer() {
        android.content.Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        android.content.SharedPreferences prefs = getSharedPreferences("duck_assist_prefs", MODE_PRIVATE);
        
        if (android.content.Intent.ACTION_ASSIST.equals(action)) {
            return prefs.getBoolean("use_drawer_assistant", true);
        } else {
            return prefs.getBoolean("use_drawer_shared", true);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        if (shouldUseDrawer()) {
            return R.layout.activity_chat;
        } else {
            return R.layout.activity_main;
        }
    }

    @Override
    protected void setActivityTheme() {
        if (!shouldUseDrawer()) {
            super.setActivityTheme();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View bottomDrawerCard = findViewById(R.id.bottomDrawerCard);
        final View drawerHeader = findViewById(R.id.drawerHeader);
        final View dismissArea = findViewById(R.id.dismissArea);

        // Turn off system transition animations since we perform our own
        overridePendingTransition(0, 0);

        // Dismiss drawer when clicking outside (dimmed background)
        if (dismissArea != null) {
            dismissArea.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissDrawer();
                }
            });
        }

        // Measure and setup drawer dynamics on layout
        if (bottomDrawerCard != null) {
            bottomDrawerCard.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    bottomDrawerCard.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    cardHeight = (int) (screenHeight * 0.85); // Drawer takes 85% of screen height when expanded
                    collapsedHeight = (int) (screenHeight * 0.55); // Drawer takes 55% of screen height when collapsed
                    
                    // Adjust card height in layout
                    android.view.ViewGroup.LayoutParams params = bottomDrawerCard.getLayoutParams();
                    if (params != null) {
                        params.height = cardHeight;
                        bottomDrawerCard.setLayoutParams(params);
                    }

                    // Start animation: Slide up from hidden state to collapsed state
                    bottomDrawerCard.setTranslationY(cardHeight); // Start fully hidden
                    bottomDrawerCard.animate()
                            .translationY(cardHeight - collapsedHeight)
                            .setDuration(300)
                            .start();
                }
            });
        }

        // Setup touch drag gesture handling on the header
        if (drawerHeader != null && bottomDrawerCard != null) {
            final float[] initialTouchY = new float[1];
            final float[] initialTranslationY = new float[1];

            drawerHeader.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (cardHeight <= 0) return false;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialTouchY[0] = event.getRawY();
                            initialTranslationY[0] = bottomDrawerCard.getTranslationY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaY = event.getRawY() - initialTouchY[0];
                            float newTranslationY = initialTranslationY[0] + deltaY;
                            
                            // Clamp translationY between 0 (fully expanded) and cardHeight (fully hidden)
                            if (newTranslationY < 0) {
                                newTranslationY = 0;
                            } else if (newTranslationY > cardHeight) {
                                newTranslationY = cardHeight;
                            }
                            bottomDrawerCard.setTranslationY(newTranslationY);
                            return true;

                        case MotionEvent.ACTION_UP:
                            float currentTranslationY = bottomDrawerCard.getTranslationY();
                            float collapsedTranslationY = cardHeight - collapsedHeight;

                            // Snap to one of the three states depending on where the user dragged it
                            if (currentTranslationY < collapsedTranslationY / 2) {
                                // Snap to fully expanded
                                bottomDrawerCard.animate()
                                        .translationY(0)
                                        .setDuration(250)
                                        .start();
                            } else if (currentTranslationY > (collapsedTranslationY + cardHeight) / 2) {
                                // Snap to hidden and finish activity
                                dismissDrawer();
                            } else {
                                // Snap back to collapsed
                                bottomDrawerCard.animate()
                                        .translationY(collapsedTranslationY)
                                        .setDuration(250)
                                        .start();
                            }
                            v.performClick();
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    private void dismissDrawer() {
        if (isDismissing) return;
        isDismissing = true;

        final View bottomDrawerCard = findViewById(R.id.bottomDrawerCard);
        if (bottomDrawerCard != null) {
            int targetY = cardHeight > 0 ? targetY = cardHeight : bottomDrawerCard.getHeight();

            // Slide the card down out of view, then finish the activity
            bottomDrawerCard.animate()
                    .translationY(targetY)
                    .setDuration(250)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            ChatActivity.super.finish();
                            overridePendingTransition(0, 0);
                        }
                    })
                    .start();
        } else {
            ChatActivity.super.finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void finish() {
        dismissDrawer();
    }
}
