package nxt.computation;

import nxt.NxtException;
import nxt.PowAndBounty;
import nxt.Transaction;
import nxt.Work;
import nxt.crypto.Crypto;
import nxt.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

// TODO: Check the entire file for unhandled exceptions

/******************************************************************************
 * Copyright © 2017 The XEL Core Developers.                                  *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

public class CommandPowBty extends IComputationAttachment {

    private long work_id;
    private boolean is_proof_of_work;
    private byte[] multiplier_or_storage;

    public CommandPowBty(long work_id, boolean is_proof_of_work, byte[] multiplier_or_storage) {
        super();
        this.work_id = work_id;
        this.is_proof_of_work = is_proof_of_work;
        this.multiplier_or_storage = multiplier_or_storage;
    }

    CommandPowBty(ByteBuffer buffer) {
        super(buffer);
        try {
            this.work_id = buffer.getLong();
            this.is_proof_of_work = (buffer.get() == (byte) 0x01) ? true : false;
            short readsize = buffer.getShort();

            // Multiplier must be exact length
            if (this.is_proof_of_work && readsize != ComputationConstants.MULTIPLIER_LENGTH) {
                throw new NxtException.NotValidException("Wrong Parameters");
            }

            if (!this.is_proof_of_work && readsize > ComputationConstants.BOUNTY_STORAGE_INTS * 4) {
                throw new NxtException.NotValidException("Wrong Parameters");
            }

            multiplier_or_storage = new byte[readsize];
            buffer.get(multiplier_or_storage);
        } catch (Exception e) {
            // pass through any error
            this.work_id = 0;
            this.is_proof_of_work = false;
            this.multiplier_or_storage = new byte[0];
        }
    }

    public long getWork_id() {
        return work_id;
    }

    public boolean isIs_proof_of_work() {
        return is_proof_of_work;
    }

    @Override
    String getAppendixName() {
        return "CommandPowBty";
    }

    @Override
    int getMySize() {
        return 8 + 1 + 2 + this.multiplier_or_storage.length;
    }

    @Override
    byte getMyMessageIdentifier() {
        return CommandsEnum.POWBTY.getCode();
    }

    @Override
    public String toString() {
        return "Pow-or-bty for id " + Long.toUnsignedString(this.work_id);
    }

    @Override
    void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(this.work_id);
        buffer.put((this.is_proof_of_work == true) ? (byte) 0x01 : (byte) 0x00);
        buffer.putShort((short)this.multiplier_or_storage.length);
        buffer.put(this.multiplier_or_storage);
    }

    public byte[] getMultiplier_or_storage() {
        return multiplier_or_storage;
    }

    @Override
    boolean validate(Transaction transaction) {
        if (this.work_id == 0) return false;
        Work w = Work.getWork(this.work_id);
        if (w == null) return false;
        if (w.isClosed() == true) return false;
        // Multiplier must be exact length
        if (this.is_proof_of_work && multiplier_or_storage.length != ComputationConstants.MULTIPLIER_LENGTH) {
            return false;
        }
        if (!this.is_proof_of_work && multiplier_or_storage.length > ComputationConstants.BOUNTY_STORAGE_INTS * 4) {
            return false;
        }
        // more validation
        return true;
    }

    @Override
    void apply(Transaction transaction) {
        if (!validate(transaction))
            return;
        // Here, apply the actual package
        Logger.logInfoMessage("processing pow-or-bty for work: id=" + Long.toUnsignedString(this.work_id));
        Work w = Work.getWork(this.work_id);
        if (w != null && w.isClosed() == false) {
            // Apply the Pow or BTY here
            PowAndBounty.addPowBty(transaction, this);
        }
    }

    public byte[] getHash() {
        final MessageDigest dig = Crypto.sha256();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeLong(this.work_id);
            dos.write(this.multiplier_or_storage);
            dos.writeBoolean(this.is_proof_of_work); // distinguish between pow and bounty
            dos.close();
        } catch (final IOException ignored) {

        }
        byte[] longBytes = baos.toByteArray();
        if (longBytes == null) longBytes = new byte[0];
        dig.update(longBytes);
        return dig.digest();
    }
}