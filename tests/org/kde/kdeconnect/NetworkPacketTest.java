/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.kde.kdeconnect;

import android.test.AndroidTestCase;
import android.util.Log;

import org.json.JSONException;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.skyscreamer.jsonassert.JSONAssert;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

class NetworkPacketTest extends AndroidTestCase {

    public void testNetworkPacket() throws JSONException {
        NetworkPacket np = new NetworkPacket("com.test");

        np.set("hello", "hola");
        assertEquals(np.getString("hello", "bye"), "hola");

        np.set("hello", "");
        assertEquals(np.getString("hello", "bye"), "");

        assertEquals(np.getString("hi", "bye"), "bye");

        np.set("foo", "bar");
        String serialized = np.serialize();
        NetworkPacket np2 = NetworkPacket.unserialize(serialized);

        assertEquals(np.getLong("id"), np2.getLong("id"));
        assertEquals(np.getString("type"), np2.getString("type"));
        assertEquals(np.getJSONArray("body"), np2.getJSONArray("body"));

        String json = "{\"id\":123,\"type\":\"test\",\"body\":{\"testing\":true}}";
        np2 = NetworkPacket.unserialize(json);
        assertEquals(np2.getId(), 123);
        assertTrue(np2.getBoolean("testing"));
        assertFalse(np2.getBoolean("not_testing"));
        assertTrue(np2.getBoolean("not_testing", true));

    }

    public void testIdentity() {

        NetworkPacket np = NetworkPacket.createIdentityPacket(getContext());

        assertEquals(np.getInt("protocolVersion"), NetworkPacket.ProtocolVersion);

    }

    public void testEncryption() throws JSONException {
        NetworkPacket original = new NetworkPacket("com.test");
        original.set("hello", "hola");

        NetworkPacket copy = NetworkPacket.unserialize(original.serialize());

        NetworkPacket decrypted = new NetworkPacket("");

        KeyPair keyPair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.genKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/initializeRsaKeys", "Exception");
            return;
        }

        PrivateKey privateKey = keyPair.getPrivate();
        assertNotNull(privateKey);

        PublicKey publicKey = keyPair.getPublic();
        assertNotNull(publicKey);

        // Encrypt and decrypt np
        assertEquals(original.getType(), "com.test");
        try {
            NetworkPacket encrypted = RsaHelper.encrypt(original, publicKey);
            assertEquals(encrypted.getType(), NetworkPacket.PACKET_TYPE_ENCRYPTED);

            decrypted = RsaHelper.decrypt(encrypted, privateKey);
            assertEquals(decrypted.getType(), "com.test");

        } catch (Exception e) {
            e.printStackTrace();
        }

        // np should be equal to np2
        assertEquals(decrypted.getLong("id"), copy.getLong("id"));
        assertEquals(decrypted.getType(), copy.getType());
        assertEquals(decrypted.getJSONArray("body"), copy.getJSONArray("body"));

        String json = "{\"body\":{\"nowPlaying\":\"A really long song name - A really long artist name\",\"player\":\"A really long player name\",\"the_meaning_of_life_the_universe_and_everything\":\"42\"},\"id\":945945945,\"type\":\"kdeconnect.a_really_really_long_package_type\"}\n";
        NetworkPacket longJsonNp = NetworkPacket.unserialize(json);
        try {
            NetworkPacket encrypted = RsaHelper.encrypt(longJsonNp, publicKey);
            decrypted = RsaHelper.decrypt(encrypted, privateKey);
            String decryptedJson = decrypted.serialize();
            JSONAssert.assertEquals(json, decryptedJson, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
