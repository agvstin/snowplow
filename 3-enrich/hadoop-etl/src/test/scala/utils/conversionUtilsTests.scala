/*
 * Copyright (c) 2012-2013 SnowPlow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich.hadoop
package utils

// Java
import java.net.URI

// Specs2 & Scalaz-Specs2
import org.specs2.{Specification, ScalaCheck}
import org.specs2.matcher.DataTables
import org.specs2.scalaz.ValidationMatchers
import org.scalacheck._
import org.scalacheck.Arbitrary._

/**
 * Tests the explodeUri function
 */
class ExplodeUriTest extends Specification with DataTables {

  def is =
    "Exploding URIs into their component pieces with explodeUri should work" ! e1

  def e1 =
    "SPEC NAME"               || "URI"                                                                           | "EXP. SCHEME" | "EXP. HOST"             | "EXP. PORT" | "EXP. PATH"                             | "EXP. QUERY"       | "EXP. FRAGMENT" |
    "With path, qs & #"       !! "http://www.psychicbazaar.com/oracles/119-psycards-deck.html?view=print#detail" ! "http"        ! "www.psychicbazaar.com" ! 80          ! Some("/oracles/119-psycards-deck.html") ! Some("view=print") ! Some("detail")  |
    "With path & space in qs" !! "http://psy.bz/genre/all/type/all?utm_source=google&utm_medium=cpc&utm_term=buy%2Btarot&utm_campaign=spring_sale" ! "http" ! "psy.bz" ! 80  ! Some("/genre/all/type/all") ! Some("utm_source=google&utm_medium=cpc&utm_term=buy+tarot&utm_campaign=spring_sale") ! None |
    "With path & no www"      !! "http://snowplowanalytics.com/analytics/index.html"                             ! "http"        ! "snowplowanalytics.com" ! 80          ! Some("/analytics/index.html")           ! None               ! None            |
    "Port specified"          !! "http://www.nbnz.co.nz:440/login.asp"                                           ! "http"        ! "www.nbnz.co.nz"        ! 440         ! Some("/login.asp")                      ! None               ! None            |
    "HTTPS & #"               !! "https://www.lancs.ac.uk#footer"                                                ! "https"       ! "www.lancs.ac.uk"       ! 80          ! None                                    ! None               ! Some("footer")  |
    "www2 & trailing /"       !! "https://www2.williamhill.com/"                                                 ! "https"       ! "www2.williamhill.com"  ! 80          ! Some("/")                               ! None               ! None            |
    "Tab & newline in qs"     !! "http://www.ebay.co.uk/sch/i.html?_from=R40&_trksid=m570.l2736&_nkw=%09+Clear+Quartz+Point+Rock+Crystal%0ADowsing+Pendulum" ! "http" ! "www.ebay.co.uk" ! 80 ! Some("/sch/i.html") ! Some("_from=R40&_trksid=m570.l2736&_nkw=    +Clear+Quartz+Point+Rock+CrystalDowsing+Pendulum") ! None |
    "Tab & newline in path"   !! "https://snowplowanalytics.com/analytic%0As/index%09nasty.html"                 ! "https"       ! "snowplowanalytics.com" ! 80          ! Some("/analytics/index    nasty.html")  ! None               ! None            |
    "Tab & newline in #"      !! "http://psy.bz/oracles/psycards.html?view=print#detail%09is%0Acorrupted"        ! "http"        ! "psy.bz"                ! 80          ! Some("/oracles/psycards.html")          ! Some("view=print") ! Some("detail    iscorrupted") |> {

      (_, uri, scheme, host, port, path, query, fragment) => {
        val actual = ConversionUtils.explodeUri(new URI(uri))
        val expected = ConversionUtils.UriComponents(scheme, host, port, path, query, fragment)       
        actual  must_== expected
      }
    }
}

/**
 * Tests the decodeBase64Url function
 */
// TODO: note that we have some functionality tweaks planned.
// See comments on ConversionUtils.decodeBase64Url for details.
class DecodeBase64UrlTest extends Specification with DataTables with ValidationMatchers with ScalaCheck { def is =

  "This is a specification to test the decodeBase64Url function"                         ^
                                                                                        p^
  "decodeBase64Url should return failure if passed a null"                               ! e1^
  "decodeBase64Url should not return failure on any other string"                        ! e2^
  "decodeBase64Url should correctly decode valid Base64 (URL-safe) encoded strings"      ! e3^
                                                                                         end

  val FieldName = "e"

  // Only way of getting a failure currently
  def e1 =
    ConversionUtils.decodeBase64Url(FieldName, null) must beFailing("Field [%s]: exception Base64-decoding [null] (URL-safe encoding): [null]".format(FieldName))

  // No string creates a failure
  def e2 =
    check { (str: String) => ConversionUtils.decodeBase64Url(FieldName, str) must beSuccessful }

  // Taken from:
  // 1. Lua Tracker's base64_spec.lua 
  // 2. Manual tests of the JavaScript Tracker's trackUnstructEvent()
  // 3. Misc edge cases worth checking
  def e3 =
    "SPEC NAME"             || "ENCODED STRING"                                 | "EXPECTED"                                 |
    "Lua Tracker String #1" !! "Sm9oblNtaXRo"                                   ! "JohnSmith"                                |
    "Lua Tracker String #2" !! "am9obitzbWl0aA"                                 ! "john+smith"                               |
    "Lua Tracker String #3" !! "Sm9obiBTbWl0aA"                                 ! "John Smith"                               |
    "Lua Tracker JSON #1"   !! "eyJhZ2UiOjIzLCJuYW1lIjoiSm9obiJ9"               ! """{"age":23,"name":"John"}"""             |
    "Lua Tracker JSON #2"   !! "eyJteVRlbXAiOjIzLjMsIm15VW5pdCI6ImNlbHNpdXMifQ" ! """{"myTemp":23.3,"myUnit":"celsius"}"""   |
    "Lua Tracker JSON #3"   !! "eyJldmVudCI6InBhZ2VfcGluZyIsIm1vYmlsZSI6dHJ1ZSwicHJvcGVydGllcyI6eyJtYXhfeCI6OTYwLCJtYXhfeSI6MTA4MCwibWluX3giOjAsIm1pbl95IjotMTJ9fQ"  ! """{"event":"page_ping","mobile":true,"properties":{"max_x":960,"max_y":1080,"min_x":0,"min_y":-12}}"""  |
    "Lua Tracker JSON #4"   !! "eyJldmVudCI6ImJhc2tldF9jaGFuZ2UiLCJwcmljZSI6MjMuMzksInByb2R1Y3RfaWQiOiJQQlowMDAzNDUiLCJxdWFudGl0eSI6LTIsInRzdGFtcCI6MTY3ODAyMzAwMH0" ! """{"event":"basket_change","price":23.39,"product_id":"PBZ000345","quantity":-2,"tstamp":1678023000}""" |
    "JS Tracker JSON #1"    !! "eyJwcm9kdWN0X2lkIjoiQVNPMDEwNDMiLCJjYXRlZ29yeSI6IkRyZXNzZXMiLCJicmFuZCI6IkFDTUUiLCJyZXR1cm5pbmciOnRydWUsInByaWNlIjo0OS45NSwic2l6ZXMiOlsieHMiLCJzIiwibCIsInhsIiwieHhsIl0sImF2YWlsYWJsZV9zaW5jZSRkdCI6MTU4MDF9" ! """{"product_id":"ASO01043","category":"Dresses","brand":"ACME","returning":true,"price":49.95,"sizes":["xs","s","l","xl","xxl"],"available_since$dt":15801}""" |
    "Unescaped characters"  !! "äöü - &"                                        ! ""           |
    "Blank string"          !! ""                                               ! ""           |> {

    (_, str, expected) => {
      ConversionUtils.decodeBase64Url(FieldName, str) must beSuccessful(expected)
    }
  }                                                                             
}
