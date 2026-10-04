package com.mosman.thrum

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * First launch: screens 1 to 7, exactly as drawn in `docs/design/screens/thrum-screens.png`.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    var screenIndex by remember { mutableStateOf(0) }

    BackHandler(enabled = screenIndex in 1..6) { screenIndex -= 1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField),
    ) {
        when {
            !capability.usable -> CapabilityDeadEnd(capability)
            screenIndex == 0 -> Screen1Splash(onNext = { screenIndex = 1 })
            screenIndex == 1 -> Screen2Welcome(
                onSeeHow = { screenIndex = 2 },
                onSkip = onDone,
            )
            screenIndex == 2 -> Screen3SoundToTouch(onNext = { screenIndex = 3 })
            screenIndex == 3 -> Screen4Sources(onNext = { screenIndex = 4 })
            screenIndex == 4 -> Screen5FeelCalls(
                onTurnOn = {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    screenIndex = 5
                },
                onMaybeLater = { screenIndex = 5 },
            )
            screenIndex == 5 -> Screen6FeelMusic(onNext = { screenIndex = 6 })
            screenIndex == 6 -> Screen7YouAreSet(onDone = onDone)
        }
    }
}

@Composable
private fun Screen1Splash(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(ThrumAccent.copy(alpha = 0.22f), Color.Transparent),
                    radius = 800f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            PulseRibbon(
                pattern = "afro",
                height = 110.dp,
                barWidth = 2.dp,
                barGap = 2.dp,
                glow = true,
            )
            Text(
                text = "THRUM",
                color = ThrumAccent,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = "Hear it. Feel it.",
                color = ThrumInk,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Checking your phone's motor…",
                color = ThrumInk2,
                fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
private fun Screen2Welcome(onSeeHow: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 0)
        Text(
            text = "THRUM",
            color = ThrumAccent,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = "Hear it.\nFeel it.",
            color = ThrumInk,
            fontSize = 39.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Thrum turns your calls, music and videos into vibrations you can feel.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThrumIcon(name = "check", tint = ThrumAccent, size = 18.dp)
            Text(
                text = "Your phone's motor can change strength, so Thrum works here.",
                color = ThrumInk,
                fontSize = 12.5.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "See how it works",
            onClick = onSeeHow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ThrumTextButton(
            text = "Skip",
            onClick = onSkip,
            color = ThrumInk2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Screen3SoundToTouch(onNext: () -> Unit) {
    val ctx = LocalContext.current
    var playingAndroid by remember { mutableStateOf(false) }
    var playingThrum by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 1)
        Text(
            text = "Turn sound into touch",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            text = "Thrum finds the beats in a song and plays them on your phone's vibration motor.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        // Diagram Card
        ThrumCard(
            modifier = Modifier.padding(top = 20.dp),
            padding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PulseRibbon(pattern = "afro", height = 44.dp, barWidth = 2.dp, barGap = 1.dp, mirror = true)
                    Text("The sound", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(modifier = Modifier.weight(1f)) {
                    PulseRibbon(pattern = "afro", height = 44.dp, barWidth = 2.dp, barGap = 1.dp)
                    Text("The beats", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(
                    modifier = Modifier.width(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ThrumIcon(name = "vib", tint = ThrumAccent, size = 34.dp)
                    Text("Your phone", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Feel the difference Card
        ThrumCard(
            modifier = Modifier.padding(top = 12.dp),
            padding = PaddingValues(16.dp),
        ) {
            Text(
                text = "FEEL THE DIFFERENCE",
                color = ThrumInk2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Android today", color = ThrumInk2, fontSize = 12.5.sp)
                    PulseRibbon(pattern = "buzz", height = 24.dp, barWidth = 2.dp, barGap = 1.dp)
                }
                CirclePlayButton(
                    playing = playingAndroid,
                    onClick = {
                        playingAndroid = !playingAndroid
                        if (playingAndroid) {
                            Haptics.play(ctx, Demo.systemBuzz())
                        } else {
                            Haptics.stop(ctx)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("With Thrum: Afro Groove, with sound", color = ThrumInk2, fontSize = 12.5.sp)
                    PulseRibbon(pattern = "afro", height = 24.dp, barWidth = 2.dp, barGap = 1.dp)
                }
                CirclePlayButton(
                    playing = playingThrum,
                    onClick = {
                        playingThrum = !playingThrum
                        if (playingThrum) {
                            Haptics.play(ctx, Demo.rhythm())
                        } else {
                            Haptics.stop(ctx)
                        }
                    },
                )
            }
        }

        Text(
            text = "Afro Groove is a Thrum Original, built into the app, so this needs no permission at all.",
            color = ThrumInk2,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Screen4Sources(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 2)
        Text(
            text = "Calls, music and videos",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            text = "Anything with a beat can become something you feel.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        ThrumCard(
            modifier = Modifier.padding(top = 22.dp),
            padding = PaddingValues(0.dp),
        ) {
            SourceListItem(
                icon = "phone",
                title = "Calls",
                subtitle = "Feel your song when someone calls, even with the ringer on.",
            )
            Divider(color = ThrumRule, thickness = 1.dp)
            SourceListItem(
                icon = "music",
                title = "Music",
                subtitle = "Play the songs on your phone and feel every beat.",
            )
            Divider(color = ThrumRule, thickness = 1.dp)
            SourceListItem(
                icon = "video",
                title = "Videos",
                subtitle = "Feel the sound from a video.",
            )
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SourceListItem(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconCircle(icon = icon, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ThrumInk2, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Screen5FeelCalls(onTurnOn: () -> Unit, onMaybeLater: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 3)

        // Incoming Call Card
        ThrumCard(
            modifier = Modifier.padding(top = 24.dp),
            padding = PaddingValues(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "INCOMING CALL",
                    color = ThrumInk2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                ThrumIcon(name = "phone", tint = ThrumAccent, size = 18.dp)
            }
            PulseRibbon(
                pattern = "afro",
                height = 56.dp,
                barWidth = 3.dp,
                barGap = 1.dp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "AIZO, but it's lofi hiphop · on the motor",
                color = ThrumInk2,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = "Feel your calls",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Pick one song for calls. When your phone rings, Thrum plays its beat on the motor, with the ringer on or on vibrate.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "To know a call has started, Thrum needs to see call notifications. It checks one thing: is this a call? Nothing leaves your phone.",
            color = ThrumInk2,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text = "Turn on call access",
            onClick = onTurnOn,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ThrumTextButton(
            text = "Maybe later",
            onClick = onMaybeLater,
            color = ThrumInk2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Screen6FeelMusic(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 4)

        // Music preview Card
        ThrumCard(
            modifier = Modifier.padding(top = 24.dp),
            padding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(64.dp)) {
                    PulseRibbon(pattern = "afro", height = 30.dp, barWidth = 2.dp, barGap = 1.dp, progress = 0.3f)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AIZO, but it's lofi hiphop", color = ThrumInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Hear and feel · 0:53 / 2:57", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
                }
                CirclePlayButton(playing = true, onClick = {}, size = 38.dp, iconSize = 13.dp)
            }
            PulseRibbon(
                pattern = "afro",
                height = 64.dp,
                barWidth = 2.dp,
                barGap = 1.dp,
                progress = 0.3f,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Text(
            text = "Play and feel your music",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Play any song on your phone inside Thrum and feel the vibration change with the music. Or turn the sound off and feel the rhythm alone.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Screen7YouAreSet(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        ThrumCard(
            modifier = Modifier.padding(top = 28.dp).height(140.dp),
            backgroundColor = ThrumSurface,
            borderColor = ThrumRule,
        ) {}

        Text(
            text = "You're set",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Thrum ships with a few pieces to start from. Scan your music whenever you're ready.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Go to your music",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CapabilityDeadEnd(capability: Haptics.Capability) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = "THRUM",
            color = ThrumAccent,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            text = "This phone can't do it",
            color = ThrumWarn,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            text = "Thrum needs a motor that can change strength. Yours has one speed. Every rhythm would reach you as the same flat buzz your phone already makes, and no setting, update, or future version of this app will change that.",
            color = Color(0xFFD6D6CF),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Motor: ${if (capability.hasVibrator) "yes" else "none"}. Strength control: ${if (capability.amplitudeControl) "yes" else "no"}.",
            color = ThrumInk2,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 12.dp),
        )

        Box(
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(52.dp)
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(26.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No button here, on purpose",
                color = ThrumInk2,
                fontSize = 13.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        }
    }
}
