package com.acidgoose.possumkotlin

import android.content.Context
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.json.JSONArray
import org.json.JSONObject

const val TWEET_LOOKUP_1 = "https://api.twitter.com/2/tweets/"
const val TWEET_LOOKUP_2 = "?expansions=attachments.media_keys&media.fields=url"



/**
 * retrieves url to an image
 */
interface UrlFinder {
    /**
     * retrieve uri for the image to be downloaded
     */
    suspend fun getImageUrl(context : Context) : String
}

/**
 * Finds link on Mastodon
 */
class MastodonUrl : UrlFinder{

    override suspend fun getImageUrl(context: Context): String {
        val client = HttpClient(Android)
        val httpResponse = client.get("https://eu-isoe-west-1.com/api/v1/accounts/113572901986925692/statuses?limit=1")

        if (httpResponse.status.value in 200..299) {
            val stringBody: String = httpResponse.body()

            return JSONArray(stringBody)
                .getJSONObject(0)
                .getJSONArray("media_attachments")
                .getJSONObject(0)
                .getString("url")
        }

        return ""
    }
}

