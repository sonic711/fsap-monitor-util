package com.fsap.monitor.sftp.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fsap.monitor.sftp.transfer.impl.ApacheFtpImpl;
import com.fsap.monitor.sftp.transfer.impl.JSchSftpImpl;
import com.fsap.monitor.sftp.vo.RemoteInfo;

class FileTransferUtilsTest {

    @Test
    void missingUrlReportsTheSettingInsteadOfRegexNullPointer() {
        assertThatThrownBy(() -> FileTransferUtils.newInstance(new RemoteInfo()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote.url")
                .hasMessageContaining("config/application.yml");
    }

    @Test
    void blankUrlReportsTheMissingSetting() {
        RemoteInfo remote = new RemoteInfo();
        remote.setUrl(" \t ");
        assertThatThrownBy(() -> FileTransferUtils.newInstance(remote))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote.url");
    }

    @Test
    void missingConnectionSettingsAreRejected() {
        assertThatThrownBy(() -> FileTransferUtils.newInstance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connection settings");
    }

    @Test
    void validSettingsStillCreateTheExpectedTransferWithoutConnecting() {
        RemoteInfo remote = new RemoteInfo();
        remote.setUrl("sftp://127.0.0.1:22");
        assertThat(FileTransferUtils.newInstance(remote)).isInstanceOf(JSchSftpImpl.class);
        remote.setUrl("ftp://127.0.0.1:21");
        assertThat(FileTransferUtils.newInstance(remote)).isInstanceOf(ApacheFtpImpl.class);
    }
}
