Phantom Proxy 1.3.0

Phantom Proxy �́Ajava�œ��삷��Web�����c�[���ł��B
�ڍׂ͈ȉ����Q�Ƃ��Ă��������B
http://code.google.com/p/coco-aweb/

�P�D�C���X�g�[�����@
 ���O����
Phantom Proxy�́AJava SE 6�ȏ��java����œ��삵�܂��B�ȉ������Q�Ƃ�java���s�����������Ă��������B
http://java.sun.com/javase/ja/6/download.html

1)�ȉ����p�b�P�[�W���_�E�����[�h���Ă��������B
http://coco-aweb.googlecode.com/files/phantom-1.3.0.zip

2)�_�E�����[�h�����t�@�C����C�ӂ̃f�B���N�g���ɉ𓀁A�W�J���Ă��������B
�R�}���h��j
 >jar xvf phantom.1.3.0.zip
 �ȍ~�A�W�J�����f�B���N�g����${phantom}�Ƃ��܂��B

3)�N���V�F����ҏW���Ă��������B
 �N���V�F���͈ȉ��ɂ���܂��B���ꂼ��ȉ��̒l��ݒ肵�Ă��������B
 Windows�̏ꍇ
  ${phantom}\bin\run.bat
  set JAVA_HOME=java�C���X�g�[���f�B���N�g��
 Unix�n�̏ꍇ
  ${phantom}/bin/run.sh
  JAVA_HOME=java�C���X�g�[���f�B���N�g��
  
  �ȉ��R�}���h�Ŏ��s����t�����܂��B
   #chmod +x ${phantom}/bin/run.sh

4)�ȉ���`�t�@�C���Ƀl�b�g���[�N�����L�q���Ă��������B
 ${phantom}/ph/setting/ph.env.properties
 �C���X�g�[�������T�[�o�̏���ݒ肵�Ă��������B
---------
 #This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
 #Information from the browsers to point to this server
 phantom.selfDomain=�u���E�U���炱�̃T�[�o���ĂԂ��߂�ip��������host��
 phantom.selfPort=�T�[�o�𓮍삳����|�[�g�ԍ�
---------
 �ȍ~${selfDomain},${selfPort}�Ƃ��܂��B���[�J���v���L�V�[�Ƃ��Ďg�p����ꍇ�́A�ҏW�̕K�v�͂���܂���B
(${selfDomain}:127.0.0.1,${selfPort}:1280)
 ���̃T�[�o����web�A�N�Z�X����ۂɎg�p����proxy�T�[�o�̏���ݒ肵�Ă��������B
---------
 #This setting is enabled when the cleanup and initial startup.Referred to by ph.ini.properties
 #Proxy information used by this server
 phantom.pacUrl=�����\���X�N���v�g�ɂ��proxy�𓮓I�ɐݒ肵�Ă���ꍇ
 phantom.proxyServer=http proxy��ip�܂��̓z�X�g��
 phantom.sslProxyServer=https proxy��ip�܂��̓z�X�g��
 phantom.exceptProxyDomains=proxy���g��Ȃ��z�X�g�ꗗ��ݒ肵�Ă��������B
 phantom.pacUrl���w�肳�ꂽ�ꍇ�A�ȍ~�͖�������܂��B��������ݒ肵�Ȃ��ꍇ�Aproxy�T�[�o�𗘗p���܂���B
---------

�Q�D�N�����@
1)�N���V�F�������s���Ă��������B
 Windows�̏ꍇ
    >${phantom}\bin\run.bat
 Unix�n�̏ꍇ
    #${phantom}\bin\run.sh
 �ȉ��̃��b�Z�[�W���R���\�[���ɏo�͂���܂��B
---------
 create CONFIG table�i����̂݁j
 generate key cn:127.0.0.1 exitValue:0�i����̂݁j
 mainHost listen start address:0.0.0.0/0.0.0.0:1280
---------
 �N���V�F����"cleanup"������t�������ꍇ�A�����̐ݒ���e���N���A���ċN�����܂��B
2)�ȉ����u���E�U����\���ł���悤�ɂȂ�܂��B
 Phntom Proxy Home
   http://${selfDomain}:${selfPort}/
 �����l:http://127.0.0.1:1280/
 Phntom Proxy Console�i�ȍ~Console)
   http://${selfDomain}:${selfPort}/admin
 �����l:http://127.0.0.1:1280/admin �������O�C�����:admin/admin


