# -*- coding: utf-8 -*-
"""Ajoute le widget « Ma collection et boosters » au projet Android créé par « npx cap add android ».

Copie le code Java (android-config/widget/java), les ressources (android-config/widget/res),
met le logo de l'appli dans le widget et déclare le widget dans AndroidManifest.xml.
Avec --test : ajoute aussi le test automatique (android-config/widget/androidTest).
"""
import os
import shutil
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
APP = 'android/app/src/main'
PKG = 'com/appeldescartes/app'


def copie(src, dst):
    n = 0
    for racine, _, fichiers in os.walk(src):
        for f in fichiers:
            a = os.path.join(racine, f)
            b = os.path.join(dst, os.path.relpath(a, src))
            os.makedirs(os.path.dirname(b), exist_ok=True)
            shutil.copyfile(a, b)
            n += 1
    return n


def main():
    if not os.path.isdir(APP):
        sys.exit('dossier %s introuvable : lance d’abord « npx cap add android »' % APP)
    n = copie(os.path.join(ICI, 'java'), os.path.join(APP, 'java', PKG))
    n += copie(os.path.join(ICI, 'res'), os.path.join(APP, 'res'))
    nodpi = os.path.join(APP, 'res', 'drawable-nodpi')
    os.makedirs(nodpi, exist_ok=True)
    for nom in ('adc_w_logo.png', 'adc_w_preview.png'):
        src = os.path.join(ICI, nom) if os.path.isfile(os.path.join(ICI, nom)) else 'docs/icon-192.png'
        shutil.copyfile(src, os.path.join(nodpi, nom))
        n += 1
    man = os.path.join(APP, 'AndroidManifest.xml')
    t = open(man, encoding='utf-8').read()
    if 'AdcWidget' not in t:
        bloc = '''
        <receiver android:name=".AdcWidget" android:exported="false" android:label="@string/adc_w_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/adc_widget_info" />
        </receiver>
        <activity android:name=".AdcPickActivity" android:exported="false" android:excludeFromRecents="true"
            android:noHistory="true" android:taskAffinity="" android:theme="@android:style/Theme.Translucent.NoTitleBar" />
    </application>'''
        assert t.count('</application>') == 1, 'AndroidManifest.xml inattendu'
        t = t.replace('</application>', bloc.lstrip('\n'), 1)
        open(man, 'w', encoding='utf-8').write(t)
    if '--test' in sys.argv:
        n += copie(os.path.join(ICI, 'androidTest'), os.path.join('android/app/src/androidTest/java', PKG))
    print('Widget ajouté : %d fichiers, manifeste à jour' % n)
    assert 'launchMode="singleTask"' in t, 'MainActivity doit rester en singleTask'


main()
