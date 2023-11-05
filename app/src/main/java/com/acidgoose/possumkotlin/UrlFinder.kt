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
 * Finds link on Twitter
 */
class TwitterUrl : UrlFinder {

    override suspend fun getImageUrl(context : Context): String {
        val jsonRecentTweets =
            makeJsonRequest(context, context.getString(R.string.twitter_account_link))
        val tweetId = jsonRecentTweets.getJSONArray("data").getJSONObject(0).get("id")
        val jsonLastTweet = makeJsonRequest(context, TWEET_LOOKUP_1 + tweetId + TWEET_LOOKUP_2)

        return jsonLastTweet.getJSONObject("includes")
            .getJSONArray("media")
            .getJSONObject(0)
            .getString("url")
    }

    /**
     * Make an http request that returns a json
     * uses authentication for twitter api
     */
    private suspend fun makeJsonRequest(context : Context, url : String) : JSONObject {
        val client = HttpClient(Android)

        val httpResponse = client.get(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer " + context.getString(R.string.api_token))
                append(HttpHeaders.UserAgent, "ktor client")
            }
        }

        if (httpResponse.status.value in 200..299) {
            val stringBody: String = httpResponse.body()
            return JSONObject(stringBody)
        }
        return JSONObject()
    }
}

/**
 * Finds link on Mastodon
 */
class MastodonUrl : UrlFinder{

    override suspend fun getImageUrl(context: Context): String {
        val client = HttpClient(Android)
        val httpResponse = client.get("https://botsin.space/api/v1/accounts/109536299782193051/statuses?limit=1")

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

