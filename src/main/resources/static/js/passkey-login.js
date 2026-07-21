function base64UrlEncode(buffer) {
    const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
    return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

function base64UrlDecode(value) {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
    const binary = window.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(result.error || `Server responded with HTTP ${response.status}`);
    }
    return result;
}

async function signInWithPasskey(button) {
    if (!window.PublicKeyCredential) {
        throw new Error('Passkeys require a supported browser and HTTPS.');
    }

    const options = await postJson(button.dataset.optionsUrl);
    const publicKey = {
        ...options,
        challenge: base64UrlDecode(options.challenge),
        allowCredentials: (options.allowCredentials || []).map((credential) => ({
            ...credential,
            id: base64UrlDecode(credential.id)
        }))
    };
    const credential = await navigator.credentials.get({ publicKey });
    const assertion = credential.response;
    const result = await postJson(button.dataset.loginUrl, {
        id: credential.id,
        rawId: base64UrlEncode(credential.rawId),
        response: {
            authenticatorData: base64UrlEncode(assertion.authenticatorData),
            clientDataJSON: base64UrlEncode(assertion.clientDataJSON),
            signature: base64UrlEncode(assertion.signature),
            userHandle: assertion.userHandle ? base64UrlEncode(assertion.userHandle) : null
        },
        type: credential.type,
        authenticatorAttachment: credential.authenticatorAttachment
    });
    window.location.href = result.redirectUrl || button.dataset.defaultUrl;
}

const button = document.getElementById('passkeySignIn');
const error = document.getElementById('passkeyLoginError');
button.addEventListener('click', async () => {
    button.disabled = true;
    error.style.display = 'none';
    try {
        await signInWithPasskey(button);
    } catch (exception) {
        button.disabled = false;
        error.textContent = exception.name === 'NotAllowedError'
            ? 'Passkey sign-in was cancelled or no matching passkey was found.'
            : exception.message;
        error.style.display = 'block';
    }
});
