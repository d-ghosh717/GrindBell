package com.grindbell.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.clickable
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.grindbell.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

class GrindBellWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(android.graphics.Color.parseColor("#F8F9FA")))
                    .padding(16)
            ) {
                // Header
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "⚡ GrindBell",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(android.graphics.Color.parseColor("#1A1A2E"))
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "Open →",
                        style = TextStyle(
                            color = ColorProvider(android.graphics.Color.parseColor("#4A6CF7")),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = GlanceModifier.clickable(
                            actionStartActivity(Intent(context, MainActivity::class.java))
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(12))

                repeat(3) { index ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(16)
                                .cornerRadius(8)
                                .background(
                                    if (index == 0)
                                        ColorProvider(android.graphics.Color.parseColor("#4A6CF7"))
                                    else
                                        ColorProvider(android.graphics.Color.parseColor("#E5E7EB"))
                                )
                        ) { }
                        Spacer(modifier = GlanceModifier.width(8))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Task ${index + 1}",
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(android.graphics.Color.parseColor("#1A1A2E"))
                                )
                            )
                            Text(
                                text = if (index == 0) "Overdue ⚠" else "Pending",
                                style = TextStyle(
                                    color = ColorProvider(
                                        if (index == 0) android.graphics.Color.parseColor("#FF6B6B")
                                        else android.graphics.Color.parseColor("#6B7280")
                                    )
                                )
                            )
                        }
                        Text(
                            text = "Done",
                            style = TextStyle(
                                color = ColorProvider(android.graphics.Color.parseColor("#4A6CF7")),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = GlanceModifier.clickable(
                                actionStartActivity(Intent(context, MainActivity::class.java))
                            )
                        )
                    }
                }
            }
        }
    }
}

@AndroidEntryPoint
class GrindBellWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GrindBellWidget()
}
