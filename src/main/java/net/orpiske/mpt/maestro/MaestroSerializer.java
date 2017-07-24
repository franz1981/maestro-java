/*
 *  Copyright ${YEAR} ${USER}
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.orpiske.mpt.maestro;


import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

import java.io.IOException;

public class MaestroSerializer {

    public static byte[] serialize(MaestroNote note) throws IOException {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();

        packer.packShort(note.getNoteType().getValue());
        packer.packLong(note.getMaestroCommand().getValue());

        packer.close();

        return packer.toByteArray();
    }
}
